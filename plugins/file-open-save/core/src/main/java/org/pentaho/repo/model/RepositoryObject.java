/*
 * Copyright 2017 Hitachi Vantara. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 */

package org.pentaho.repo.model;

import org.pentaho.di.repository.ObjectId;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by bmorrise on 5/16/17.
 */
public abstract class RepositoryObject {
  private String name;
  private ObjectId objectId;
  private String path;
  private int depth;
  private String parent;
  private boolean hasChildren = false;
  private String extension;
  private Date date;
  protected String type;
  private String repository;

  private List<RepositoryObject> children = new ArrayList<>();

  public void addChild( RepositoryObject repositoryObject ) {
    this.children.add( repositoryObject );
  }

  public List<RepositoryObject> getChildren() {
    return children;
  }

  public String getName() {
    return name;
  }

  public void setName( String name ) {
    this.name = name;
  }

  public ObjectId getObjectId() {
    return objectId;
  }

  public void setObjectId( ObjectId objectId ) {
    this.objectId = objectId;
  }

  public String getPath() {
    return path;
  }

  public void setPath( String path ) {
    this.path = path;
  }

  public int getDepth() {
    return depth;
  }

  public void setDepth( int depth ) {
    this.depth = depth;
  }

  public String getParent() {
    return parent;
  }

  public void setParent( String parent ) {
    this.parent = parent;
  }

  public boolean isHasChildren() {
    return hasChildren;
  }

  public void setHasChildren( boolean hasChildren ) {
    this.hasChildren = hasChildren;
  }

  public String getExtension() {
    return extension;
  }

  public void setExtension( String extension ) {
    this.extension = extension;
  }

  public Date getDate() {
    return date;
  }

  public void setDate( Date date ) {
    this.date = date;
  }

  public abstract String getType();

  public String getRepository() {
    return repository;
  }

  public void setRepository( String repository ) {
    this.repository = repository;
  }
}
