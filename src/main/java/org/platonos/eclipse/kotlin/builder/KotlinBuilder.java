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

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;
import org.jetbrains.jet.cli.common.messages.OutputMessageUtil.Output;

import com.intellij.util.containers.HashMap;

/**
 * @author Evert Tigchelaar (everttigchelaar@gmail.com)
 */
public class KotlinBuilder extends IncrementalProjectBuilder {

	private KotlinProject kotlinProject;
	
	private List<IResource> classes = new ArrayList<IResource>();
	
	private Map<String, List<String>> sourceMapping = new HashMap<String, List<String>>();
	
	private static final String KOTLIN_EXT = "kt";
	
	@Override
	protected void startupOnInitialize() {
		super.startupOnInitialize();
		try {
			kotlinProject = KotlinProject.create(getProject());
			cleanKotlinMarkers();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}	
	
	class KotlinDeltaVisitor implements IResourceDeltaVisitor {
		
		private List<IResource> kotlinResources = new ArrayList<IResource>();
		
		private List<IResource> removedResources = new ArrayList<IResource>();
		
		/*
		 * (non-Javadoc)
		 * 
		 * @see org.eclipse.core.resources.IResourceDeltaVisitor#visit(org.eclipse.core.resources.IResourceDelta)
		 */
		public boolean visit(IResourceDelta delta) throws CoreException {
			IResource resource = delta.getResource();
						
			switch (delta.getKind()) {
			case IResourceDelta.REMOVED:
				if (checkKotlin(resource)) {
					deleteClasses(resource);
					removedResources.add(resource);
				}				
				break;
			case IResourceDelta.ADDED: { 
				checkAdded(resource, kotlinResources);
				if (isKotlinSource(resource)) {
					return false;
				}
				break;
			}
			case IResourceDelta.CHANGED:
				checkChanged(resource, kotlinResources);
				if (checkKotlin(resource)) {
					return false;
				}
				break;
			}
			//return true to continue visiting children.
			return true;
		}
		
		public List<IResource> getKotlinResources() {
			return kotlinResources;
		}
		
		public List<IResource> getRemovedResources() {
			return removedResources;
		}
	}

	class KotlinResourceVisitor implements IResourceVisitor {
		
		private List<IResource> kotlinResources = new ArrayList<IResource>();
		
		public boolean visit(IResource resource) {
			if (checkKotlin(resource)) {			
				kotlinResources.add(resource);
			}
			//return true to continue visiting children.			
			return true;
		}
		
		public List<IResource> getKotlinResources() {
			return kotlinResources;
		}
		
	}

	public static final String BUILDER_ID = "EclipseKotlin.kotlinBuilder";

	private static final String MARKER_TYPE = "EclipseKotlin.kotlinProblem";

	void addMarker(IFile file, String message, int lineNumber,
			int severity) {
		try {
			IMarker marker = file.createMarker(MARKER_TYPE);
			marker.setAttribute(IMarker.MESSAGE, message);
			marker.setAttribute(IMarker.SEVERITY, severity);
			if (lineNumber == -1) {
				lineNumber = 1;
			}
			marker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
		} catch (CoreException e) {
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.core.internal.events.InternalBuilder#build(int,
	 *      java.util.Map, org.eclipse.core.runtime.IProgressMonitor)
	 */
	protected IProject[] build(int kind, Map<String, String> args, IProgressMonitor monitor)
			throws CoreException {
		if (kind == FULL_BUILD) {
			fullBuild(monitor);
		} else {
			IResourceDelta delta = getDelta(getProject());
			if (delta == null) {
				fullBuild(monitor);
			} else {
				incrementalBuild(delta, monitor);
			}
		}
		return getProject().getReferencedProjects();
	}
	
	private void checkAdded(IResource resource, List<IResource> changedKotlinResources) {
		if (resource instanceof IFile && resource.getName().endsWith(".class")) {
			classes.add(resource);
		}
		checkChanged(resource, changedKotlinResources);
	}
	
	private void checkChanged(IResource resource, List<IResource> changedKotlinResources) {
		if (checkKotlin(resource)) {
			changedKotlinResources.add(resource);
		}
	}
	
	private boolean checkKotlin(IResource resource) {		
		if (isKotlinSource(resource)) {
			deleteMarkers(resource);
			return true;
		}
		
		return false;
	}
	
	private boolean isKotlinSource(IResource resource) {
		if (resource instanceof IFile && resource.getFileExtension().equals(KOTLIN_EXT)) {			
			try {
				for (IClasspathEntry entry : kotlinProject.getRawClasspath()) {
					if (IClasspathEntry.CPE_SOURCE == entry.getEntryKind()) {
						IPath path = entry.getPath();
						IPath fullPath = resource.getFullPath();
						
						if (path.isPrefixOf(fullPath)) {
							return true;
						}
					}					
				}
			} catch (JavaModelException e) {
			}
		} 
		
		return false;
	}
	
	private void deleteClasses(IResource resource) {
		IPath fullPath = resource.getLocation();
		
		List<String> list = sourceMapping.get(fullPath.toString());
		
		if (list != null) {
			for (Iterator<String> iter = list.iterator(); iter.hasNext();) {
				String path = iter.next();
				iter.remove();
				File f = new File(path);
				
				if (f.exists()) {
					f.delete();
				}
			}
			
			if (list.isEmpty()) {
				sourceMapping.remove(fullPath.toString());
			}
		}
	}

	private void deleteMarkers(IResource res) {		
		try {
			res.deleteMarkers(MARKER_TYPE, false, IResource.DEPTH_ZERO);
		} catch (CoreException ce) {
		}
	}

	protected void fullBuild(final IProgressMonitor monitor)
			throws CoreException {
		KotlinResourceVisitor visitor = new KotlinResourceVisitor();
		getProject().accept(visitor);
		
		List<IResource> resources = visitor.getKotlinResources();		
		
		try {
			monitor.beginTask("Compiling Kotlin sources - FULL", resources.size());		
			compileSources(resources);
		} finally {
			monitor.done();
		}
	}

	protected void incrementalBuild(IResourceDelta delta,
			IProgressMonitor monitor) throws CoreException {
		// the visitor does the work.
		KotlinDeltaVisitor visitor = new KotlinDeltaVisitor();		
		delta.accept(visitor);
		compileSources(visitor.getKotlinResources());
	}
	
	@Override
	protected void clean(IProgressMonitor monitor) throws CoreException {
		super.clean(monitor);
		monitor.beginTask("Clean Kotlin markers", 1);
		try {
			cleanKotlinMarkers();
		} finally {
			monitor.done();
		}
	}
	
	private void cleanKotlinMarkers() throws CoreException {
		getProject().accept(new IResourceVisitor() {
			@Override
			public boolean visit(IResource resource) throws CoreException {
				if (isKotlinSource(resource)) {
					deleteMarkers(resource);
				}				
				return false;
			}
		});
	}
	
	private void compileSources(List<IResource> kotlinResources) throws JavaModelException {
		if (!kotlinResources.isEmpty()) {
			KotlinEclipseCompiler compiler = new KotlinEclipseCompiler();
			compiler.setSources(kotlinResources);
			compiler.setOutputDir(getOutputDir());
			
			List<String> classPathList = new ArrayList<String>();
			
			for (IClasspathEntry entry : kotlinProject.getRawClasspath()) {
				if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
					IPath path = resolveToProject(entry.getPath());
					classPathList.add(path.toFile().getAbsolutePath());
				}
			}
			
			String[] classpath = classPathList.toArray(new String[classPathList.size()]);			
			compiler.setClasspath(classpath);
			
			compiler.setMessageCollector(new EclipseMessageCollector(this, kotlinResources));
			compiler.compile();
		}		
	}
	
	private IPath getOutputDir() throws JavaModelException {
		IPath path = kotlinProject.getOutputLocation();
		return resolveToProject(path);
	}
	
	private IPath resolveToProject(IPath path) {
		IJavaProject javaProject = kotlinProject.getJavaProject();
		IPath location = javaProject.getProject().getLocation();
		location = location.removeLastSegments(1);
		return location.append(path);
	}
	
	public void reportOutput(Output output) {
		File outputFile = output.outputFile;
		Collection<File> sourceFiles = output.sourceFiles;
		
		for (File sourceFile : sourceFiles) {
			String sourcePath = sourceFile.getAbsolutePath().replace(File.separatorChar, '/');
			
			List<String> list = sourceMapping.get(sourcePath);
			if (list == null) {
				list = new ArrayList<String>();
				sourceMapping.put(sourcePath, list);
			}
			
			list.add(outputFile.getAbsolutePath().replace(File.separatorChar, '/'));
		}
		
		
		/*
		String[] sources = sourceName.split("\n");
		String[] outputs = outputName.split("\n");
		
		for (String source : sources) {
			if (source.length() > 0) {
				source = source.replace(File.separator, "/");
				
				for (String output : outputs) {
					if (output.length() > 0) {
						
						List<String> list = sourceMapping.get(source);
						if (list == null) {
							list = new ArrayList<>();
							sourceMapping.put(source, list);
						}
						
						output = output.replace(File.separator, "/");						
						String outputDir = getOutputDir();
						
						File out = new File(outputDir, output);						
						list.add(out.getAbsolutePath());
					}
				}				
			}			
		}
		*/		
	}
	
}
