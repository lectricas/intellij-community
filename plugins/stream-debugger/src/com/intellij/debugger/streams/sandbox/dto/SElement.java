// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.sandbox.dto;

import com.intellij.xdebugger.frame.XNamedValue;
import com.sun.jdi.Type;
import org.jetbrains.annotations.NotNull;

public abstract class SElement extends XNamedValue {

  Difference whatChanged = Difference.NOTHING;

  protected SElement(@NotNull String name) {
    super(name);
  }

  abstract Type getType();

  enum Difference {
    NAME, VALUE, TYPE, NOTHING
  }
}
