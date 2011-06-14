/*******************************************************************************
 * Copyright (c) 2008 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.maven.ide.eclipse.wtp;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.maven.project.MavenProject;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jst.j2ee.project.facet.AppClientFacetInstallDataModelProvider;
import org.eclipse.jst.j2ee.project.facet.IAppClientFacetInstallDataModelProperties;
import org.eclipse.wst.common.frameworks.datamodel.DataModelFactory;
import org.eclipse.wst.common.frameworks.datamodel.IDataModel;
import org.eclipse.wst.common.project.facet.core.IFacetedProject;
import org.eclipse.wst.common.project.facet.core.IFacetedProject.Action;
import org.eclipse.wst.common.project.facet.core.IProjectFacetVersion;
import org.eclipse.wst.common.project.facet.core.ProjectFacetsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Application Client project configurator delegate
 * 
 * @author Fred Bricon
 */
@SuppressWarnings("restriction")
class AppClientProjectConfiguratorDelegate extends AbstractProjectConfiguratorDelegate {

  private static final Logger log = LoggerFactory.getLogger(AppClientProjectConfiguratorDelegate.class); 

  protected void configure(IProject project, MavenProject mavenProject, IProgressMonitor monitor)
      throws CoreException {
    IFacetedProject facetedProject = ProjectFacetsManager.create(project, true, monitor);

    if(facetedProject.hasProjectFacet(WTPProjectsUtil.APP_CLIENT_FACET)) {
      try {
        facetedProject.modify(Collections.singleton(new IFacetedProject.Action(IFacetedProject.Action.Type.UNINSTALL,
            facetedProject.getInstalledVersion(WTPProjectsUtil.APP_CLIENT_FACET), null)), monitor);
      } catch(Exception ex) {
        log.error("Error removing Application client facet", ex);
      }
    }

    Set<Action> actions = new LinkedHashSet<Action>();
    installJavaFacet(actions, project, facetedProject);

    IFile manifest = null;
    IFolder firstInexistentfolder = null;
    boolean manifestAlreadyExists =false;
    // WTP doesn't allow facet versions changes for JEE facets 
    if(!facetedProject.hasProjectFacet(WTPProjectsUtil.APP_CLIENT_FACET)) {
      // Configuring content directory, used by WTP to create META-INF/manifest.mf, ejb-jar.xml
      AcrPluginConfiguration config = new AcrPluginConfiguration(mavenProject);
      String contentDir = config.getContentDirectory(project);
      IFolder contentFolder = project.getFolder(contentDir);
      manifest = contentFolder.getFile("META-INF/MANIFEST.MF");
      manifestAlreadyExists =manifest.exists(); 
      if (!manifestAlreadyExists) {
        firstInexistentfolder = findFirstInexistentFolder(project, contentFolder, manifest);
      }   
      
      IDataModel appClientModelCfg = DataModelFactory.createDataModel(new AppClientFacetInstallDataModelProvider());
      appClientModelCfg.setProperty(IAppClientFacetInstallDataModelProperties.CONFIG_FOLDER, contentDir);

      IProjectFacetVersion fv = config.getFacetVersion();
      
      actions.add(new IFacetedProject.Action(IFacetedProject.Action.Type.INSTALL, fv, appClientModelCfg));
    }

    if(!actions.isEmpty()) {
      facetedProject.modify(actions, monitor);
    }

    //MECLIPSEWTP-41 Fix the missing moduleCoreNature
    fixMissingModuleCoreNature(project, monitor);
    
    removeTestFolderLinks(project, mavenProject, monitor, "/");

    if (!manifestAlreadyExists && manifest != null && manifest.exists()) {
      manifest.delete(true, monitor);
    }
    if (firstInexistentfolder != null && firstInexistentfolder.exists() && firstInexistentfolder.members().length == 0 )
    {
      firstInexistentfolder.delete(true, monitor);
    }
    
    //Remove "library unavailable at runtime" warning.
    setNonDependencyAttributeToContainer(project, monitor);
    
}

  public void setModuleDependencies(IProject project, MavenProject mavenProject, IProgressMonitor monitor)
      throws CoreException {
    // TODO check if there's anything to do!
  }
}