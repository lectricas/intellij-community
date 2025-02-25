// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.threading;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.threading.EmptySynchronizedStatementInspection;

/**
 * @author Fabrice TIERCELIN
 */
public class EmptySynchronizedStatementFixTest extends IGQuickFixesTestCase {
  @Override
  protected BaseInspection getInspection() {
    return new EmptySynchronizedStatementInspection();
  }

  public void testRemoveSynchronizedStatement() {
    doMemberTest(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "synchronized"),
                 "  public void printName(String lock) {\n" +
                 "    synchronized/**/(lock) {}\n" +
                 "}\n",
                 "  public void printName(String lock) {\n" +
                 "}\n"
    );
  }

  public void testDoNotFixUsedSynchronizedStatement() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "synchronized"),
                               "class X {\n" +
                               "  public void printName(String lock) {\n" +
                               "    synchronized(lock) {\n" +
                               "      System.out.println(lock);\n" +
                               "    }\n" +
                               "  }\n" +
                               "}\n");
  }
}
