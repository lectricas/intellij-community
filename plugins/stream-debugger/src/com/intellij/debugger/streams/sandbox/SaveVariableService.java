// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.sandbox;

import com.intellij.debugger.streams.sandbox.dto.SElement;
import com.intellij.openapi.components.Service;

@Service
public final class SaveVariableService {

  private SElement variable = null;

  public void saveVariable(SElement element) {
    this.variable = element;
  }

  public SElement getVariable() {
    return variable;
  }
}