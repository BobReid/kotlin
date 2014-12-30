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

package org.jetbrains.kotlin.codegen.optimization.common

import org.jetbrains.org.objectweb.asm.tree.AbstractInsnNode
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.tree.analysis.Frame
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicValue
import org.jetbrains.org.objectweb.asm.tree.MethodNode

val AbstractInsnNode.isMeaningful : Boolean get() =
    when (this.getType()) {
        AbstractInsnNode.LABEL, AbstractInsnNode.LINE, AbstractInsnNode.FRAME -> false
        else -> true
    }

class InsnStream(val from: AbstractInsnNode, val to: AbstractInsnNode?) : Stream<AbstractInsnNode> {
    override fun iterator(): Iterator<AbstractInsnNode> {
        return object : Iterator<AbstractInsnNode> {
            var current = from
            override fun next(): AbstractInsnNode {
                val result = current
                current = current.getNext()
                return result
            }
            override fun hasNext() = current != to
        }
    }
}

fun MethodNode.prepareForEmitting() {
    tryCatchBlocks = tryCatchBlocks.filter { tcb ->
        InsnStream(tcb.start, tcb.end).any { insn ->
            insn.isMeaningful
        }
    }

    var current = instructions.getLast()
    while (!current.isMeaningful) {
        val prev = current.getPrevious()

        if (current.getType() == AbstractInsnNode.LINE) {
            instructions.remove(current)
        }

        current = prev
    }
}
