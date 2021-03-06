/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.lower

import org.jetbrains.kotlin.backend.common.ScopeWithIr
import org.jetbrains.kotlin.backend.common.lower.SingleAbstractMethodLowering
import org.jetbrains.kotlin.backend.common.phaser.makeIrFilePhase
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.backend.jvm.ir.erasedUpperBound
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.load.java.JavaVisibilities

internal val singleAbstractMethodPhase = makeIrFilePhase(
    ::JvmSingleAbstractMethodLowering,
    name = "SingleAbstractMethod",
    description = "Replace SAM conversions with instances of interface-implementing classes"
)

private class JvmSingleAbstractMethodLowering(context: JvmBackendContext) : SingleAbstractMethodLowering(context) {
    override fun getWrapperVisibility(expression: IrTypeOperatorCall, scopes: List<ScopeWithIr>) =
        if (inInlineFunctionScope) Visibilities.PUBLIC else JavaVisibilities.PACKAGE_VISIBILITY

    override fun getSuperTypeForWrapper(typeOperand: IrType): IrType =
        typeOperand.erasedUpperBound.defaultType

    private val IrType.isKotlinFunInterface: Boolean
        get() = getClass()?.origin != IrDeclarationOrigin.IR_EXTERNAL_JAVA_DECLARATION_STUB

    override val IrType.needEqualsHashCodeMethods get() = isKotlinFunInterface
}