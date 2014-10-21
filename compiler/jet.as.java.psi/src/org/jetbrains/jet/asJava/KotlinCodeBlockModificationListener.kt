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

package org.jetbrains.jet.asJava

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.*
import com.intellij.psi.impl.PsiModificationTrackerImpl
import com.intellij.psi.impl.PsiTreeChangeEventImpl.PsiEventType.*
import com.intellij.psi.impl.PsiTreeChangeEventImpl
import com.intellij.psi.impl.PsiTreeChangePreprocessor
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.jet.lang.psi.*
import com.intellij.psi.util.PsiTreeUtil

/**
 * Tested in OutOfBlockModificationTestGenerated
 */
public class KotlinCodeBlockModificationListener(modificationTracker: PsiModificationTracker) : PsiTreeChangePreprocessor {
    private val myModificationTracker = modificationTracker as PsiModificationTrackerImpl

    override fun treeChanged(event: PsiTreeChangeEventImpl) {
        if (event.getFile() !is JetFile) return

        when (event.getCode()) {
            BEFORE_CHILDREN_CHANGE,
            BEFORE_PROPERTY_CHANGE,
            BEFORE_CHILD_MOVEMENT,
            BEFORE_CHILD_REPLACEMENT,
            BEFORE_CHILD_ADDITION,
            BEFORE_CHILD_REMOVAL -> {
                // skip
            }

            CHILD_ADDED,
            CHILD_REMOVED,
            CHILD_REPLACED -> {
                processChange(event.getParent(), event.getOldChild(), event.getChild())
            }

            CHILDREN_CHANGED -> {
                if (!event.isGenericChange()) {
                    processChange(event.getParent(), event.getParent(), null)
                }
            }

            CHILD_MOVED,
            PROPERTY_CHANGED -> {
                myModificationTracker.incCounter()
            }

            else -> LOG.error("Unknown code:" + event.getCode())
        }
    }

    private fun processChange(parent: PsiElement?, child1: PsiElement?, child2: PsiElement?) {
        try {
            if (!isInsideCodeBlock(parent)) {
                if (parent != null && parent.getContainingFile() is JetFile) {
                    myModificationTracker.incCounter()
                }
                else {
                    myModificationTracker.incOutOfCodeBlockModificationCounter()
                }
                return
            }

            if (containsClassesInside(child1) || (child2 != child1 && containsClassesInside(child2))) {
                myModificationTracker.incCounter()
            }
        }
        catch (e: PsiInvalidElementAccessException) {
            myModificationTracker.incCounter() // Shall not happen actually, just a pre-release paranoia
        }
    }

    class object {
        private val LOG = Logger.getInstance("#org.jetbrains.jet.asJava.JetCodeBlockModificationListener")

        private fun containsClassesInside(element: PsiElement?): Boolean {
            if (element == null) return false
            if (element is PsiClass) return true

            var child = element.getFirstChild()
            while (child != null) {
                if (containsClassesInside(child)) return true
                child = child!!.getNextSibling()
            }

            return false
        }

        private fun isInsideCodeBlock(element: PsiElement?): Boolean {
            if (element is PsiFileSystemItem) return false
            if (element == null || element.getParent() == null) return true

            var parent = element
            while (parent !is PsiFile && parent !is PsiDirectory && parent != null) {
                if (parent is JetClass) return false // anonymous or local class

                if (parent is JetBlockExpression) {
                    if (!shouldChangeModificationCount(element)) {
                        return true;
                    }
                }

                parent = parent!!.getParent()
            }

            return false
        }

        public fun shouldChangeModificationCount(place: PsiElement): Boolean {
            // false -> inside code block
            // true -> means nothing, parent will be checked

            val declaration = PsiTreeUtil.getParentOfType<JetDeclaration>(place, javaClass<JetDeclaration>(), true)
            if (declaration == null) return true

            return when (declaration) {
                is JetNamedFunction -> {
                    val function: JetNamedFunction = declaration
                    if (function.hasDeclaredReturnType() || function.hasBlockBody()) {
                        takePartInDeclarationTypeInference(function)
                    }
                    else {
                        shouldChangeModificationCount(function)
                    }
                }
                is JetPropertyAccessor -> {
                    takePartInDeclarationTypeInference(declaration)
                }
                is JetProperty -> {
                    val property = declaration as JetProperty
                    if (property.getTypeReference() != null) {
                        takePartInDeclarationTypeInference(property)
                    }
                    else {
                        shouldChangeModificationCount(property)
                    }
                }
                is JetMultiDeclaration, is JetMultiDeclarationEntry, is JetFunctionLiteral -> {
                    shouldChangeModificationCount(declaration)
                }
                else -> {
                    true
                }
            }
        }

        private fun takePartInDeclarationTypeInference(place: PsiElement): Boolean {
            val declaration = PsiTreeUtil.getParentOfType<JetDeclaration>(place, javaClass<JetDeclaration>(), true)
            if (declaration != null) {
                if (declaration is JetNamedFunction) {
                    val function = declaration as JetNamedFunction
                    if (!function.hasDeclaredReturnType() && !function.hasBlockBody()) {
                        return true
                    }
                }
                else if (declaration is JetProperty) {
                    val property = declaration as JetProperty
                    if (property.getTypeReference() == null) {
                        return true
                    }
                }

                return takePartInDeclarationTypeInference(declaration)
            }

            return false
        }
    }
}

