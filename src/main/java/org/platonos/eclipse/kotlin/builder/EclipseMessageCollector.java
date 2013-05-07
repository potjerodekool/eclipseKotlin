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

import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IMarker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.cli.common.messages.CompilerMessageLocation;
import org.jetbrains.jet.cli.common.messages.CompilerMessageSeverity;
import org.jetbrains.jet.cli.common.messages.MessageCollector;
import org.jetbrains.jet.cli.common.messages.OutputMessageUtil;
import org.jetbrains.jet.cli.common.messages.OutputMessageUtil.Output;

/**
 * @author Evert Tigchelaar (everttigchelaar@gmail.com)
 */
public class EclipseMessageCollector implements MessageCollector {

	private final KotlinBuilder kotlinBuilder;
	private final List<IResource> kotlinResources;
	
	public EclipseMessageCollector(KotlinBuilder kotlinBuilder, List<IResource> kotlinResources) {
		this.kotlinBuilder = kotlinBuilder;
		this.kotlinResources = kotlinResources;
	}
	
	@Override
	public void report(@NotNull CompilerMessageSeverity severity,
			@NotNull String message, @NotNull CompilerMessageLocation location) {
		
		if (severity == CompilerMessageSeverity.OUTPUT) {
			Output output = OutputMessageUtil.parseOutputMessage(message);
			kotlinBuilder.reportOutput(output);			
		} else {		
			IFile file = findFile(location);
			
			if (file != null) {		
				kotlinBuilder.addMarker(file, message, location.getLine(), convert(severity));
			}
		}
	}
	
	private int convert(CompilerMessageSeverity severity) {
		switch (severity) {
			case INFO : 
				return IMarker.SEVERITY_INFO;
			case WARNING :
				return IMarker.SEVERITY_WARNING;
			case ERROR :				
			case EXCEPTION :
				return IMarker.SEVERITY_ERROR;
			//Not sure what to do yet. Use info for now.
			case LOGGING :
			case OUTPUT :
			default :
				return IMarker.SEVERITY_INFO;
		}
	}
	
	private IFile findFile(CompilerMessageLocation location) {
		String path = location.getPath();
		
		for (IResource resource : kotlinResources) {
			String abso = resource.getLocation().toFile().getAbsolutePath();
			
			if (abso.equals(path)) {
				return (IFile) resource;
			}			
		}		
		
		return null;
	}

}
