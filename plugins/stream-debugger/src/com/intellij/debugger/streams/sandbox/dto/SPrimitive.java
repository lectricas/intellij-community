// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.sandbox.dto;

import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.XValuePlace;
import com.intellij.xdebugger.frame.presentation.XKeywordValuePresentation;
import com.intellij.xdebugger.frame.presentation.XRegularValuePresentation;
import com.sun.jdi.Type;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.editor.colors.CodeInsightColors.*;

public class SPrimitive extends SElement {
  public String value;
  Type type;
  String name;

  public SPrimitive(String value, Type type, String name) {
    super(name);
    this.value = value;
    this.type = type;
    this.name = name;
  }

  @Override
  public String toString() {
    return value + ":" + type.name();
  }



  @Override
  public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
    var presentation = new XKeywordValuePresentation(value) {
      @Override
      public void renderValue(@NotNull XValueTextRenderer renderer) {
        System.out.println(whatChanged);
        switch (whatChanged) {
          case ADD:
            renderer.renderValue(value, WEAK_WARNING_ATTRIBUTES);
            break;
          case REMOVE:
            renderer.renderValue(value, DEPRECATED_ATTRIBUTES);
            break;
          case REPLACE:
            renderer.renderValue(value, MARKED_FOR_REMOVAL_ATTRIBUTES);
            break;
          case MOVE:
            renderer.renderValue(value, MARKED_FOR_REMOVAL_ATTRIBUTES);
            break;
          case COPY:
            renderer.renderValue(value, MARKED_FOR_REMOVAL_ATTRIBUTES);
            break;
          case NOTHING:
            renderer.renderValue(value);
            break;
        }
      }
    };
    node.setPresentation(null, presentation, false);
  }

  @Override
  Type getType() {
    return type;
  }
}
