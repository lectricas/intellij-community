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
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.XInspectDialog;
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
          SElement current = toElement(v, nodeName);
          SElement saved = saveVariableService.getVariable();
          saveVariableService.saveVariable(current);

          ApplicationManager.getApplication().invokeLater(() -> {

            XInspectDialog dialog = new XInspectDialog(
              session.getProject(),
              editorsProvider,
              null,
              nodeName,
              current,
              null,
              DebuggerUIUtil.getSession(e),
              true
            );
            dialog.show();
          });

          LOG.info(current.toString());
      }
    });
  }

  private static JsonElement toJsonElement(Value value) {
    JsonObject object = new JsonObject();
    if (value instanceof StringReference) {
      return new JsonPrimitive(((StringReference)value).value());
    }
    else if (value instanceof ObjectReference) {
      List<Field> fileds = ((ObjectReference)value).referenceType().allFields();
      for (Field f : fileds) {
        JsonElement elem = toJsonElement(((ObjectReference)value).getValue(f));
        object.add(f.name(), elem);
      }
      return object;
    }
    else {
      return new JsonPrimitive(value.toString());
    }
  }

  private static SElement toElement(Value value, String name) {
    if (value instanceof StringReference) {
      return new SPrimitive(((StringReference)value).value(), value.type(), name);
    }
    else if (value instanceof ObjectReference) {
      SObject o = new SObject(value.type(), name);
      List<Field> fileds = ((ObjectReference)value).referenceType().allFields();
      for (Field f : fileds) {
        SElement elem = toElement(((ObjectReference)value).getValue(f), f.name());
        o.fields.put(f.name(), elem);
      }
      return o;
    }
    else {
      if (value == null) {
        // TODO: 21.04.2022 check null for non initialized
        throw new NullPointerException();
      }
      else {
        return new SPrimitive(value.toString(), value.type(), name);
      }
    }
  }
}

