// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.sandbox;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.memory.action.DebuggerTreeAction;
import com.intellij.debugger.streams.sandbox.dto.SElement;
import com.intellij.debugger.streams.sandbox.dto.SObject;
import com.intellij.debugger.streams.sandbox.dto.SPrimitive;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.sun.jdi.Field;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.StringReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SaveVariableAction extends DebuggerTreeAction {

  protected static final Logger LOG = Logger.getInstance(SaveVariableAction.class);

  @Override
  protected void perform(XValueNodeImpl node, @NotNull String nodeName, AnActionEvent e) {
    DebugProcessImpl debugProcess = JavaDebugProcess.getCurrentDebugProcess(e);
    SaveVariableService saveVariableService = ApplicationManager.getApplication().getService(SaveVariableService.class);
    var session = debugProcess.getSession().getXDebugSession();
    final XDebuggerEditorsProvider editorsProvider = session.getDebugProcess().getEditorsProvider();
    debugProcess.getManagerThread().schedule(new DebuggerCommandImpl() {


      @Override
      protected void action() {
          XValue xValue = node.getValueContainer();
          ValueDescriptorImpl valueDescriptor = ((JavaValue)xValue).getDescriptor();
          Value v = valueDescriptor.getValue();
          SElement current = Mappers.toElement(v, nodeName);
          saveVariableService.saveVariable(current);
          LOG.info(current.toString());
      }
    });
  }
}

