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

package org.jetbrains.jet.utils.serializer

import java.io.File
import com.intellij.openapi.util.Disposer
import org.jetbrains.jet.config.CompilerConfiguration
import org.jetbrains.jet.descriptors.serialization.*
import org.jetbrains.jet.lang.descriptors.*
import org.jetbrains.jet.lang.resolve.name.Name
import java.io.ByteArrayOutputStream
import org.jetbrains.jet.lang.types.lang.BuiltInsSerializationUtil
import com.intellij.openapi.Disposable
import org.jetbrains.jet.config.CommonConfigurationKeys
import org.jetbrains.jet.lang.resolve.name.FqName
import org.jetbrains.jet.utils.recursePostOrder
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.jet.lang.resolve.java.JvmAnalyzerFacade
import org.jetbrains.jet.context.GlobalContext
import org.jetbrains.jet.analyzer.ModuleInfo
import org.jetbrains.jet.lang.resolve.java.JvmPlatformParameters
import org.jetbrains.jet.analyzer.ModuleContent
import org.jetbrains.jet.lang.resolve.kotlin.DeserializedResolverUtils
import org.jetbrains.jet.lang.resolve.scopes.DescriptorKindFilter
import org.jetbrains.jet.lang.psi.JetFile
import kotlin.platform.platformStatic
import org.json.JSONObject
import com.intellij.openapi.util.io.FileUtil
import javax.xml.bind.DatatypeConverter.printBase64Binary

private object BuiltInsSerializerExtension : SerializerExtension() {
    override fun serializeClass(descriptor: ClassDescriptor, proto: ProtoBuf.Class.Builder, stringTable: StringTable) {
        for (annotation in descriptor.getAnnotations()) {
            proto.addExtension(BuiltInsProtoBuf.classAnnotation, AnnotationSerializer.serializeAnnotation(annotation, stringTable))
        }
    }

    override fun serializePackage(
            packageFragments: Collection<PackageFragmentDescriptor>,
            proto: ProtoBuf.Package.Builder,
            stringTable: StringTable
    ) {
        val classes = packageFragments.flatMap {
            it.getMemberScope().getDescriptors(DescriptorKindFilter.CLASSIFIERS).filterIsInstance<ClassDescriptor>()
        }

        for (descriptor in DescriptorSerializer.sort(classes)) {
            proto.addExtension(BuiltInsProtoBuf.className, stringTable.getSimpleNameIndex(descriptor.getName()))
        }
    }

    override fun serializeCallable(
            callable: CallableMemberDescriptor,
            proto: ProtoBuf.Callable.Builder,
            stringTable: StringTable
    ) {
        for (annotation in callable.getAnnotations()) {
            proto.addExtension(BuiltInsProtoBuf.callableAnnotation, AnnotationSerializer.serializeAnnotation(annotation, stringTable))
        }
    }

    override fun serializeValueParameter(
            descriptor: ValueParameterDescriptor,
            proto: ProtoBuf.Callable.ValueParameter.Builder,
            stringTable: StringTable
    ) {
        for (annotation in descriptor.getAnnotations()) {
            proto.addExtension(BuiltInsProtoBuf.parameterAnnotation, AnnotationSerializer.serializeAnnotation(annotation, stringTable))
        }
    }
}

public class Serializer() {
    private var totalSize = 0
    private var totalFiles = 0

    public fun serialize(moduleDescriptor: ModuleDescriptor, files: List<JetFile>, meta: File) {
        val jsonObject: JSONObject = JSONObject()
        files.map { it.getPackageFqName() }.toSet().forEach {
            fqName -> serializePackage(moduleDescriptor, fqName, jsonObject)
        }
        FileUtil.writeToFile(meta, jsonObject.toString())
    }

    fun serializePackage(module: ModuleDescriptor, fqName: FqName, jsonObject: JSONObject) {
        val packageView = module.getPackage(fqName) ?: error("No package resolved in $module")

        // TODO: perform some kind of validation? At the moment not possible because DescriptorValidator is in compiler-tests
        // DescriptorValidator.validate(packageView)

        val serializer = DescriptorSerializer.createTopLevel(BuiltInsSerializerExtension)

        val classifierDescriptors = DescriptorSerializer.sort(packageView.getMemberScope().getDescriptors(DescriptorKindFilter.CLASSIFIERS))

        ClassSerializationUtil.serializeClasses(classifierDescriptors, serializer, object : ClassSerializationUtil.Sink {
            override fun writeClass(classDescriptor: ClassDescriptor, classProto: ProtoBuf.Class) {
                val stream = ByteArrayOutputStream()
                classProto.writeTo(stream)
                write(jsonObject, getFileName(classDescriptor), stream)
            }
        })

        val packageStream = ByteArrayOutputStream()
        val fragments = module.getPackageFragmentProvider().getPackageFragments(fqName)
        val packageProto = serializer.packageProto(fragments).build() ?: error("Package fragments not serialized: $fragments")
        packageProto.writeTo(packageStream)
        write(jsonObject, BuiltInsSerializationUtil.getPackageFilePath(fqName), packageStream)

        val nameStream = ByteArrayOutputStream()
        NameSerializationUtil.serializeStringTable(nameStream, serializer.getStringTable())
        write(jsonObject, BuiltInsSerializationUtil.getStringTableFilePath(fqName), nameStream)
    }

    fun write(jsonObject: JSONObject, fileName: String, stream: ByteArrayOutputStream) {
        totalSize += stream.size()
        totalFiles++
        jsonObject.put(fileName, printBase64Binary(stream.toByteArray()))
    }

    fun getFileName(classDescriptor: ClassDescriptor): String {
        return BuiltInsSerializationUtil.getClassMetadataPath(DeserializedResolverUtils.getClassId(classDescriptor))!!
    }
}
