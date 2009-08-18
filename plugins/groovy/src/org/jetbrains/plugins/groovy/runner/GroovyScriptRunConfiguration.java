/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.runner;

import com.intellij.execution.CantRunException;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.config.GroovyFacet;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import java.nio.charset.Charset;
import java.util.regex.Pattern;

public class GroovyScriptRunConfiguration extends AbstractGroovyScriptRunConfiguration {
  private static final String GROOVY_STARTER = "org.codehaus.groovy.tools.GroovyStarter";
  private static final String GROOVY_MAIN = "groovy.ui.GroovyMain";

  // JVM parameters
  @NonNls public static final String DGROOVY_STARTER_CONF = "-Dgroovy.starter.conf=";
  @NonNls public static final String DTOOLS_JAR = "-Dtools.jar=";
  @NonNls public static final String DGROOVY_HOME = "-Dgroovy.home=";

  public GroovyScriptRunConfiguration(ConfigurationFactory factory, Project project, String name) {
    super(name, project, factory);
  }

  @Override
  protected boolean isValidModule(Module module) {
    return GroovyFacet.getInstance(module) != null;
  }

  protected ModuleBasedConfiguration createInstance() {
    return new GroovyScriptRunConfiguration(getFactory(), getConfigurationModule().getProject(), getName());
  }

  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new GroovyRunConfigurationEditor();
  }

  private void configureJavaParams(JavaParameters params, Module module, final String groovyHome) throws CantRunException {
    params.setCharset(null);
    params.setJdk(ModuleRootManager.getInstance(module).getSdk());

    params.setWorkingDirectory(getAbsoluteWorkDir());

    final Pattern pattern = Pattern.compile(".*[\\\\/]groovy[^\\\\/]*jar");
    groovyJar:
    for (Library library : GroovyConfigUtils.getInstance().getSDKLibrariesByModule(module)) {
      for (VirtualFile root : library.getFiles(OrderRootType.CLASSES)) {
        if (pattern.matcher(root.getPresentableUrl()).matches()) {
          params.getClassPath().add(root);
          break groovyJar;
        }
      }
    }

    //add starter configuration parameters
    params.getVMParametersList().addParametersString(DGROOVY_HOME + "\"" + groovyHome + "\"");

    // -Dgroovy.starter.conf

    String confpath = getConfPath(groovyHome);

    params.getVMParametersList().add(DGROOVY_STARTER_CONF + confpath);

    // -Dtools.jar
    Sdk jdk = params.getJdk();
    if (jdk != null && jdk.getSdkType() instanceof JavaSdkType) {
      String toolsPath = ((JavaSdkType)jdk.getSdkType()).getToolsPath(jdk);
      if (toolsPath != null) {
        params.getVMParametersList().add(DTOOLS_JAR + toolsPath);
      }
    }

    // add user parameters
    params.getVMParametersList().addParametersString(vmParams);

    // set starter class
    params.setMainClass(GROOVY_STARTER);
  }

  private void configureGroovyStarter(JavaParameters params, final Module module, boolean isTests, final VirtualFile scriptFile,
                                      final String groovyHome) throws CantRunException {
    // add GroovyStarter parameters
    params.getProgramParametersList().add("--main");
    params.getProgramParametersList().add(GROOVY_MAIN);

    params.getProgramParametersList().add("--conf");
    String confpath = getConfPath(groovyHome);
    params.getProgramParametersList().add(confpath);

    params.getProgramParametersList().add("--classpath");

    // Clear module libraries from JDK's occurrences
    final JavaParameters tmp = new JavaParameters();
    tmp.configureByModule(module, isTests ? JavaParameters.JDK_AND_CLASSES_AND_TESTS : JavaParameters.JDK_AND_CLASSES);
    StringBuffer buffer = RunnerUtil.getClearClassPathString(tmp, module);

    params.getProgramParametersList().add(buffer.toString());
    if (isDebugEnabled) {
      params.getProgramParametersList().add("--debug");
    }
    addScriptEncodingSettings(params, scriptFile);
  }

  private void addScriptEncodingSettings(final JavaParameters params, final VirtualFile scriptFile) {
    //Setting up script charset
    // MUST be last parameter
    Charset charset = EncodingProjectManager.getInstance(getProject()).getEncoding(scriptFile, true);
    if (charset == null) {
      charset = EncodingManager.getInstance().getDefaultCharset();
      if (!Comparing.equal(CharsetToolkit.getDefaultSystemCharset(), charset)) {
        params.getProgramParametersList().add("--encoding=" + charset.displayName());
      }
    }
    else {
      params.getProgramParametersList().add("--encoding=" + charset.displayName());
    }
  }

  @Override
  protected void configureCommandLine(JavaParameters params, Module module, boolean tests, VirtualFile script) throws CantRunException {
    final String groovyHome = StringUtil.notNullize(LibrariesUtil.getGroovyHomePath(module));
    configureJavaParams(params, module, groovyHome);
    configureGroovyStarter(params, module, tests, script, groovyHome);

    params.getProgramParametersList().add(scriptPath);
    params.getProgramParametersList().addParametersString(scriptParams);
  }

}
