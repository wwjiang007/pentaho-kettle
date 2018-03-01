/*! ******************************************************************************
 *
 * Pentaho Data Integration
 *
 * Copyright (C) 2002-2018 by Hitachi Vantara : http://www.pentaho.com
 *
 *******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package org.pentaho.di.trans.streaming.common;

import org.pentaho.di.core.CheckResult;
import org.pentaho.di.core.CheckResultInterface;
import org.pentaho.di.core.RowMetaAndData;
import org.pentaho.di.core.database.DatabaseMeta;
import org.pentaho.di.core.encryption.Encr;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.injection.Injection;
import org.pentaho.di.core.injection.bean.BeanInjectionInfo;
import org.pentaho.di.core.injection.bean.BeanInjector;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.row.value.ValueMetaString;
import org.pentaho.di.core.util.Utils;
import org.pentaho.di.core.variables.VariableSpace;
import org.pentaho.di.core.xml.XMLHandler;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.repository.ObjectId;
import org.pentaho.di.repository.Repository;
import org.pentaho.di.resource.ResourceEntry;
import org.pentaho.di.resource.ResourceReference;
import org.pentaho.di.trans.StepWithMappingMeta;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.step.StepMetaInterface;
import org.pentaho.metastore.api.IMetaStore;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;


public abstract class BaseStreamStepMeta extends StepWithMappingMeta implements StepMetaInterface {


  private static final Class<?> PKG = BaseStreamStep.class;  // for i18n purposes, needed by Translator2!!   $NON-NLS-1$
  public static final String TRANSFORMATION_PATH = "TRANSFORMATION_PATH";
  public static final String NUM_MESSAGES = "NUM_MESSAGES";
  public static final String DURATION = "DURATION";
  public static final String PASSWORD = "PASSWORD";

  @Injection ( name = TRANSFORMATION_PATH )  // pull this stuff up to common
  protected String transformationPath = "";

  @Injection ( name = NUM_MESSAGES )
  protected String batchSize = "1000";

  @Injection ( name = DURATION )
  protected String batchDuration = "1000";

  @Override public String getXML() {
    BeanInjectionInfo info = new BeanInjectionInfo( this.getClass() );
    BeanInjector injector = new BeanInjector( info );
    Map<String, BeanInjectionInfo.Property> properties = info.getProperties();

    StringBuilder builder = new StringBuilder();
    info.getGroups()
      .forEach( appendXmlForGroup( injector, properties, builder ) );
    return builder.toString();
  }

  /**
   * Maps a group of injector properties to XML tags, wrapping them in a parent element
   * <GROUPNAME></GROUPNAME>
   * if a GroupName has been defined.
   */
  private Consumer<BeanInjectionInfo.Group> appendXmlForGroup( BeanInjector injector,
                                                               Map<String, BeanInjectionInfo.Property> properties,
                                                               StringBuilder builder ) {
    return group -> {
      boolean groupHasName = !isNullOrEmpty( group.getName() );
      if ( groupHasName ) {
        builder.append( "<" ).append( group.getName() ).append( ">\n" );
      }
      List<BeanInjectionInfo.Property> groupProps = group.getGroupProperties();
      builder.append( getXmlForProps( injector,

        properties.entrySet().stream()
          .filter( entry -> groupProps.contains( entry.getValue() ) )
          .collect( Collectors.toMap( Map.Entry::getKey, Map.Entry::getValue ) )
      ) );
      if ( groupHasName ) {
        builder.append( "</" ).append( group.getName() ).append( ">\n" );
      }
    };
  }

  private String getXmlForProps( BeanInjector injector, Map<String, BeanInjectionInfo.Property> properties ) {
    return properties.entrySet().stream()
      .map( entry -> {
        try {
          String key = entry.getKey();
          Object obj = injector.getObject( this, key );
          if ( entry.getValue().pathArraysCount == 1 ) {
            @SuppressWarnings ( "unchecked" )
            List<String> list = (List<String>) obj;
            return list.stream()
              .map( v -> XMLHandler.addTagValue( key, v ) )
              .collect( Collectors.joining() );
          }
          // Suffix PASSWORD to all elements that need to be encrypted/decrypted
          String value = isPassword( key )
            ? Encr.encryptPasswordIfNotUsingVariables( obj.toString() ) : obj.toString();
          return XMLHandler.addTagValue( key, value );
        } catch ( Exception e ) {
          throw new RuntimeException( e );
        }
      } ).collect( Collectors.joining() );
  }

  private boolean isPassword( String attributeName ) {
    return attributeName.toUpperCase().contains( PASSWORD );
  }

  @Override public void loadXML(
    Node stepnode, List<DatabaseMeta> databases, IMetaStore metaStore ) {

    BeanInjectionInfo info = new BeanInjectionInfo( this.getClass() );
    BeanInjector injector = new BeanInjector( info );
    info.getProperties().values().forEach( property -> {
      try {
        injector.setProperty(
          this, property.getName(), nodesToRowMetaAndData( stepnode, property ), property.getName() );
      } catch ( KettleException e ) {
        throw new RuntimeException( e );
      }
    } );
  }

  private List<RowMetaAndData> nodesToRowMetaAndData( Node stepnode, BeanInjectionInfo.Property name ) {
    List<Node> nodes = getChildrenForProp( stepnode, name );

    if ( nodes.size() == 0 ) {
      // return placeholder
      RowMetaAndData rmad = new RowMetaAndData();
      rmad.addValue( new ValueMetaString( name.getName() ), "" );
      return Collections.singletonList( rmad );
    } else {
      return nodes.stream()
        .map( node -> {
          RowMetaAndData rmad = new RowMetaAndData();
          String nodeName = node.getNodeName();
          // Suffix PASSWORD to all elements that need to be encrypted/decrypted
          Object nodeValue = isPassword( nodeName )
            ? Encr.decryptPasswordOptionallyEncrypted( node.getTextContent() ) : node.getTextContent();
          rmad.addValue( new ValueMetaString( node.getNodeName() ), nodeValue );
          return rmad;
        } )
        .collect( Collectors.toList() );
    }
  }

  private List<Node> getChildrenForProp( Node stepnode, BeanInjectionInfo.Property property ) {

    String groupName = property.getGroupName();
    List<Node> groupNodeList = XMLHandler.getNodes( stepnode, groupName );
    checkState( groupNodeList.size() == 0 || groupNodeList.size() == 1,
      "Expecting only zero or one group of name %s within xml.",
      isNullOrEmpty( groupName ) ? "(empty)" : groupName );
    if ( groupNodeList.size() == 1 ) {
      return XMLHandler.getNodes( groupNodeList.get( 0 ), property.getName() );

    }
    return XMLHandler.getNodes( stepnode, property.getName() );
  }

  @Override public void readRep( Repository rep, IMetaStore metaStore, ObjectId
    id_step, List<DatabaseMeta> databases )
    throws KettleException {
    setTransformationPath( rep.getStepAttributeString( id_step, TRANSFORMATION_PATH ) );
    setFileName( rep.getStepAttributeString( id_step, TRANSFORMATION_PATH ) );
    setBatchSize( rep.getStepAttributeString( id_step, BaseStreamStepMeta.NUM_MESSAGES ) );
    setBatchDuration( rep.getStepAttributeString( id_step, BaseStreamStepMeta.DURATION ) );
  }

  @Override public void saveRep( Repository rep, IMetaStore metaStore, ObjectId transId, ObjectId stepId )
    throws KettleException {
    rep.saveStepAttribute( transId, stepId, TRANSFORMATION_PATH, transformationPath );
    rep.saveStepAttribute( transId, stepId, BaseStreamStepMeta.NUM_MESSAGES, batchSize );
    rep.saveStepAttribute( transId, stepId, BaseStreamStepMeta.DURATION, batchDuration );
  }

  public void setTransformationPath( String transformationPath ) {
    this.transformationPath = transformationPath;
  }

  public void setBatchSize( String batchSize ) {
    this.batchSize = batchSize;
  }

  public void setBatchDuration( String batchDuration ) {
    this.batchDuration = batchDuration;
  }

  @Override public void setDefault() {
    batchSize = "1000";
    batchDuration = "1000";
  }

  public String getTransformationPath() {
    return transformationPath;
  }

  public String getBatchSize() {
    return batchSize;
  }

  public String getBatchDuration() {
    return batchDuration;
  }

  public void check( List<CheckResultInterface> remarks, TransMeta transMeta,
                     StepMeta stepMeta, RowMetaInterface prev, String[] input, String[] output,
                     RowMetaInterface info, VariableSpace space, Repository repository,
                     IMetaStore metaStore ) {
    long duration = Long.MIN_VALUE;
    try {
      duration = Long.parseLong( space.environmentSubstitute( getBatchDuration() ) );
    } catch ( NumberFormatException e ) {
      remarks.add( new CheckResult(
        CheckResultInterface.TYPE_RESULT_ERROR,
        BaseMessages.getString( PKG, "BaseStreamStepMeta.CheckResult.NaN", "Duration" ),
        stepMeta ) );
    }

    long size = Long.MIN_VALUE;
    try {
      size = Long.parseLong( space.environmentSubstitute( getBatchSize() ) );
    } catch ( NumberFormatException e ) {
      remarks.add( new CheckResult(
        CheckResultInterface.TYPE_RESULT_ERROR,
        BaseMessages.getString( PKG, "BaseStreamStepMeta.CheckResult.NaN", "Number of records" ),
        stepMeta ) );
    }

    if ( duration == 0 && size == 0 ) {
      remarks.add( new CheckResult(
        CheckResultInterface.TYPE_RESULT_ERROR,
        BaseMessages.getString( PKG, "BaseStreamStepMeta.CheckResult.NoBatchDefined" ),
        stepMeta ) );
    }
  }

  @Override
  public List<ResourceReference> getResourceDependencies( TransMeta transMeta, StepMeta stepInfo ) {
    List<ResourceReference> references = new ArrayList<>( 5 );
    String realFilename = transMeta.environmentSubstitute( transformationPath );
    ResourceReference reference = new ResourceReference( stepInfo );
    references.add( reference );

    if ( !Utils.isEmpty( realFilename ) ) {
      // Add the filename to the references, including a reference to this step
      // meta data.
      //
      reference.getEntries().add( new ResourceEntry( realFilename, ResourceEntry.ResourceType.ACTIONFILE ) );
    }

    return references;
  }

  @Override public String[] getReferencedObjectDescriptions() {
    return new String[] {
      BaseMessages.getString( PKG, "BaseStreamStepMeta.ReferencedObject.SubTrans.Description" ) };
  }

  @Override public boolean[] isReferencedObjectEnabled() {
    return new boolean[] { !Utils.isEmpty( transformationPath ) };
  }

  @Override public Object loadReferencedObject( int index, Repository rep, IMetaStore metaStore, VariableSpace space )
    throws KettleException {
    return loadMappingMeta( this, rep, metaStore, space );
  }
}
