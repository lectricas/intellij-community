// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.action;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.memory.action.DebuggerTreeAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SaveVariableAction extends DebuggerTreeAction {

  protected static final Logger LOG = Logger.getInstance(SaveVariableAction.class);

  @Override
  protected void perform(XValueNodeImpl node, @NotNull String nodeName, AnActionEvent e) {
    DebugProcessImpl debugProcess = JavaDebugProcess.getCurrentDebugProcess(e);
    debugProcess.getManagerThread().schedule(new DebuggerCommandImpl() {
      @Override
      protected void action() {
        try {
          StackFrame frame = debugProcess.getDebuggerContext().getFrameProxy().getStackFrame();
          List<LocalVariable> variables = frame.visibleVariables();
          for (LocalVariable lv : variables) {
            Value v = frame.getValue(lv);
            calculate(v);
          }
        }
        catch (EvaluateException | AbsentInformationException ex) {
          LOG.info(ex);
        }
      }
    });
  }

  private void calculate(Value value) {
    if (value instanceof StringReference) {
      LOG.info(((StringReference)value).value());
    }
    else if (value instanceof ObjectReference) {
      List<Field> fileds = ((ObjectReference)value).referenceType().allFields();
      for (Field f : fileds) {
        calculate(((ObjectReference)value).getValue(f));
      }
    }
    else {
      LOG.info(value.toString());
    }
  }
}
