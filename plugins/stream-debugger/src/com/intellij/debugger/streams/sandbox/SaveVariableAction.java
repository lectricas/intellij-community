// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.sandbox;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.memory.action.DebuggerTreeAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

public class SaveVariableAction extends DebuggerTreeAction {

  protected static final Logger LOG = Logger.getInstance(SaveVariableAction.class);

  @Override
  protected void perform(XValueNodeImpl node, @NotNull String nodeName, AnActionEvent e) {
    DebugProcessImpl debugProcess = JavaDebugProcess.getCurrentDebugProcess(e);
    SaveVariableService saveVariableService =
      ApplicationManager.getApplication().getService(SaveVariableService.class);
    debugProcess.getManagerThread().schedule(new DebuggerCommandImpl() {
      @Override
      protected void action() {
        try {
          StackFrame frame = debugProcess.getDebuggerContext().getFrameProxy().getStackFrame();
          Optional<LocalVariable> selectedVariableOpt = frame.visibleVariables()
            .stream()
            .filter(variable -> variable.name().equals(nodeName))
            .findFirst();
          Value v = frame.getValue(selectedVariableOpt.orElseThrow());
          JsonElement elem = toJsonElement(v);
          saveVariableService.saveVariable(elem);
          for (JsonElement jsonElem : saveVariableService.getVariable()) {
            LOG.info(jsonElem.toString());
          }
        }
        catch (EvaluateException | AbsentInformationException ex) {
          LOG.info(ex);
        }
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
}
