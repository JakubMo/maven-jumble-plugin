package org.pragmatics.jumble;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;

import com.reeltwo.jumble.fast.FastRunner;
import com.reeltwo.jumble.ui.JumbleListener;
import com.reeltwo.jumble.ui.JumbleScorePrinterListener;

/**
 * Goal which touches a timestamp file.
 * 
 * @goal jumble
 * 
 * @requiresDependencyResolution test
 * 
 * @phase integration-test
 */
public class MyMojo extends AbstractMojo {
	/**
	 * <i>Maven Internal</i>: Project to interact with.
	 * 
	 * @parameter expression="${project}"
	 * @required
	 * @readonly
	 */
	private MavenProject project;


	public void execute() throws MojoExecutionException {
		FastRunner runner = new FastRunner();
		Set<String> classPath = new HashSet<String>();
		try {
			classPath.addAll(project.getTestClasspathElements());
			classPath.addAll(project.getCompileClasspathElements());
			classPath.addAll(project.getRuntimeClasspathElements());
			classPath.addAll(project.getSystemClasspathElements());
		} catch (DependencyResolutionRequiredException e1) {
			getLog().info(e1);
			e1.printStackTrace();
		}
		String classPathString = StringUtils.join(classPath, ";");
		getLog().info("Classpath: " + classPathString);
		runner.setClassPath(classPathString);

		String className = "org.pragmatics.jumble.App";
		List<String> testClassNames = Arrays
				.asList("org.pragmatics.jumble.AppTest");
		JumbleListener listener = new JumbleScorePrinterListener();
		try {
			runner.runJumble(className, testClassNames, listener);
		} catch (Exception e) {
			throw new MojoExecutionException("fail", e);
		}
	}
}
