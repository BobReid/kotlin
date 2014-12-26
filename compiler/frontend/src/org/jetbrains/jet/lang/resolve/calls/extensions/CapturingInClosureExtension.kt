/*
 * Copyright 2010-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.jet.lang.resolve.calls.extensions

import org.jetbrains.jet.lang.descriptors.CallableDescriptor
import org.jetbrains.jet.lang.resolve.calls.model.ResolvedCall
import org.jetbrains.jet.lang.resolve.calls.context.BasicCallResolutionContext
import org.jetbrains.jet.lang.resolve.BindingTrace
import org.jetbrains.jet.lang.resolve.scopes.JetScope
import org.jetbrains.jet.lang.descriptors.VariableDescriptor
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor
import org.jetbrains.jet.lang.resolve.BindingContext
import org.jetbrains.jet.lang.resolve.BindingContext.CAPTURED_IN_CLOSURE
import org.jetbrains.jet.lang.types.expressions.CaptureKind
import org.jetbrains.jet.lang.resolve.DescriptorToSourceUtils
import org.jetbrains.jet.lang.psi.JetFunctionLiteral
import org.jetbrains.jet.lang.psi.JetFunctionLiteralExpression
import org.jetbrains.jet.lang.resolve.calls.callUtil.*
import org.jetbrains.jet.lang.descriptors.SimpleFunctionDescriptor
import org.jetbrains.jet.lang.resolve.calls.model.VariableAsFunctionResolvedCall

class CapturingInClosureExtension : CallResolverExtension {
    override fun <F : CallableDescriptor> run(resolvedCall: ResolvedCall<F>, context: BasicCallResolutionContext) {
        val variableResolvedCall = if (resolvedCall is VariableAsFunctionResolvedCall) resolvedCall.variableCall else resolvedCall
        val variableDescriptor = variableResolvedCall.getResultingDescriptor() as? VariableDescriptor
        if (variableDescriptor != null) {
            checkCapturingInClosure(variableDescriptor, context.trace, context.scope)
        }
    }

    private fun checkCapturingInClosure(variable: VariableDescriptor, trace: BindingTrace, scope: JetScope) {
        val variableParent = variable.getContainingDeclaration()
        val scopeContainer = scope.getContainingDeclaration()
        if (scopeContainer != variableParent && variableParent is CallableDescriptor) {
            if (trace.get(CAPTURED_IN_CLOSURE, variable) != CaptureKind.NOT_INLINE) {
                val inline = isCapturedInInline(trace.getBindingContext(), scopeContainer, variableParent)
                trace.record(CAPTURED_IN_CLOSURE, variable, if (inline) CaptureKind.INLINE_ONLY else CaptureKind.NOT_INLINE)
            }
        }
    }

    private fun isCapturedInInline(
            context: BindingContext, scopeContainer: DeclarationDescriptor, variableParent: DeclarationDescriptor
    ): Boolean {
        val scopeDeclaration = DescriptorToSourceUtils.descriptorToDeclaration(scopeContainer)
        if (scopeDeclaration !is JetFunctionLiteral) return false

        val parent = scopeDeclaration.getParent()
        assert(parent is JetFunctionLiteralExpression) { "parent of JetFunctionLiteral is " + parent }
        val resolvedCall = (parent as JetFunctionLiteralExpression).getParentResolvedCall(context, true)
        if (resolvedCall == null) return false

        val callable = resolvedCall.getResultingDescriptor()
        if (callable is SimpleFunctionDescriptor && callable.getInlineStrategy().isInline()) {
            val scopeContainerParent = scopeContainer.getContainingDeclaration()
            assert(scopeContainerParent != null) { "parent is null for " + scopeContainer }
            return scopeContainerParent == variableParent || isCapturedInInline(context, scopeContainerParent, variableParent)
        }
        return false
    }
}


