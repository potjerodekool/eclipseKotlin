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

import static org.jetbrains.jet.cli.jvm.JVMConfigurationKeys.ANNOTATIONS_PATH_KEY;
import static org.jetbrains.jet.cli.jvm.JVMConfigurationKeys.CLASSPATH_KEY;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.buildtools.core.BytecodeCompiler;
import org.jetbrains.jet.cli.common.CLIConfigurationKeys;
import org.jetbrains.jet.cli.common.CompilerPlugin;
import org.jetbrains.jet.cli.common.messages.MessageCollector;
import org.jetbrains.jet.cli.common.messages.MessageCollectorPlainTextToStream;
import org.jetbrains.jet.cli.jvm.compiler.CompileEnvironmentUtil;
import org.jetbrains.jet.cli.jvm.compiler.JetCoreEnvironment;
import org.jetbrains.jet.cli.jvm.compiler.KotlinToJVMBytecodeCompiler;
import org.jetbrains.jet.config.CommonConfigurationKeys;
import org.jetbrains.jet.config.CompilerConfiguration;
import org.jetbrains.jet.utils.KotlinPaths;
import org.jetbrains.jet.utils.KotlinPathsFromHomeDir;
import org.jetbrains.jet.utils.PathUtil;

/**
 * @author Evert Tigchelaar (everttigchelaar@gmail.com)
 */
public class KotlinEclipseCompiler {

	private String stdlib;	
	private BytecodeCompiler compiler = new BytecodeCompiler();	
	private List<IResource> sources = null;
	private IPath output = null;
	private String[] classpath = null;
	
	private MessageCollector messageCollector;

	public KotlinEclipseCompiler() {
		String kotlinHome = System.getenv("KOTLIN_HOME");

		if (kotlinHome != null) {
			stdlib = kotlinHome + "/kotlin-runtime.jar";
		}
	}
	
	public void setSources(@NotNull List<IResource> sources) {
		this.sources = sources;
	}
	
	public void setOutputDir(@NotNull IPath output) {
		this.output = output;
	}
	
	public void setClasspath(@NotNull String[] classpath) {
		this.classpath = classpath;
	}
	
	public void setMessageCollector(MessageCollector messageCollector) {
		this.messageCollector = messageCollector;
	}

	public void compile() {
		sourcesToDir();
	}

	private void sourcesToDir() {
		try {
			JetCoreEnvironment environment = env(stdlib, classpath);
			File outputFile = output.toFile();
			KotlinToJVMBytecodeCompiler
					.compileBunchOfSources(environment, null, outputFile,	true);
		} catch (Exception e) {
		}
	}

    private JetCoreEnvironment env(String stdlib, String[] classpath) {
    	String[] paths = new String[sources.size()];
		int p = 0;
		
		for (IResource resource : sources) {
			String path = resource.getLocation().toFile().getAbsolutePath();			
			paths[p++] = path;
		}
    	
        CompilerConfiguration configuration = createConfiguration(stdlib, classpath, paths);
        return new JetCoreEnvironment(CompileEnvironmentUtil.createMockDisposable(), configuration);
    }
    
    private static String errorMessage() {
        return "compilation failed";
    }
    
    private CompilerConfiguration createConfiguration(String stdlib, String[] classpath, String[] sourceRoots) {
        KotlinPaths paths = getKotlinPathsForAntTask();
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.add(CLASSPATH_KEY, PathUtil.findRtJar());
        if ((stdlib != null) && (stdlib.trim().length() > 0)) {
            configuration.add(CLASSPATH_KEY, new File(stdlib));
        }
        else {
            File path = paths.getRuntimePath();
            if (path.exists()) {
                configuration.add(CLASSPATH_KEY, path);
            }
        }
        if ((classpath != null) && (classpath.length > 0)) {
            for (String path : classpath) {
                configuration.add(CLASSPATH_KEY, new File(path));
            }
        }
        File jdkAnnotationsPath = paths.getJdkAnnotationsPath();
        if (jdkAnnotationsPath.exists()) {
            configuration.add(ANNOTATIONS_PATH_KEY, jdkAnnotationsPath);
        }

        configuration.addAll(CommonConfigurationKeys.SOURCE_ROOTS_KEY, Arrays.asList(sourceRoots));
       	configuration.put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, getMessageCollector());

        // lets register any compiler plugins
        configuration.addAll(CLIConfigurationKeys.COMPILER_PLUGINS, getCompilerPlugins());
        return configuration;
    }
    
    private Collection<CompilerPlugin> getCompilerPlugins() {
		return compiler.getCompilerPlugins();
	}

	private static KotlinPaths getKotlinPathsForAntTask() {
        return new KotlinPathsFromHomeDir(PathUtil.getJarPathForClass(BytecodeCompiler.class).getParentFile().getParentFile());
    }

	private MessageCollector getMessageCollector() {
		if (messageCollector == null) {
			return MessageCollectorPlainTextToStream.PLAIN_TEXT_TO_SYSTEM_ERR;
		} else {
			return messageCollector;
		}		
	}
}
