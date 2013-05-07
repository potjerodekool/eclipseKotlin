/*
 * Copyright [2013] [Platonos]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * 	   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.platonos.eclipse.kotlin.builder;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.xml.sax.SAXException;

/**
 * @author Evert Tigchelaar (everttigchelaar@gmail.com)
 */
public class KotlinProject {
	
	private final IJavaProject javaProject;
	
	private KotlinProject(IProject project) {
		this.javaProject = JavaCore.create(project);
	}
	
	public IJavaProject getJavaProject() {
		return javaProject;
	}
	
	public IPath getOutputLocation() throws JavaModelException {
		return javaProject.getOutputLocation();
	}
		
	public IClasspathEntry[] getRawClasspath() throws JavaModelException {
		return javaProject.getRawClasspath();
	}
	
	public static KotlinProject create(IProject project) throws Exception {
		KotlinProject kotlinProject = new KotlinProject(project);
		kotlinProject.initClassPath();
		return kotlinProject;
	}
	
	private void initClassPath() throws ParserConfigurationException, SAXException, CoreException, IOException {
		String excludedResources = javaProject.getOption( JavaCore.CORE_JAVA_BUILD_RESOURCE_COPY_FILTER, true);
		
		if(excludedResources.indexOf( "*.kt" ) == -1) {
			excludedResources = excludedResources.length() == 0 ? "*.kt" : excludedResources + ",*.kt";	
			javaProject.setOption( JavaCore.CORE_JAVA_BUILD_RESOURCE_COPY_FILTER, excludedResources );	
		}	
	}

}

