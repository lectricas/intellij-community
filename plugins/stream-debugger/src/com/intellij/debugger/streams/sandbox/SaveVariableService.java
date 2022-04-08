// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.sandbox;

import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;

public class SaveVariableService {

  private List<JsonElement> variables = new ArrayList<>();

  public void saveVariable(JsonElement element) {
    variables.add(element);
  }

  public List<JsonElement> getVariable() {
    return variables;
  }
}