/*
 * Copyright 2010-2015 JetBrains s.r.o.
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

package org.jetbrains.kotlin.resolve;

import com.google.common.collect.Lists;
import com.intellij.openapi.util.Pair;
import kotlin.Function1;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.descriptors.*;
import org.jetbrains.kotlin.name.Name;
import org.jetbrains.kotlin.platform.PlatformToKotlinClassMap;
import org.jetbrains.kotlin.resolve.scopes.FilteringScope;
import org.jetbrains.kotlin.resolve.scopes.JetScope;
import org.jetbrains.kotlin.resolve.scopes.WritableScope;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public interface Importer {
    void addAllUnderImport(@NotNull DeclarationDescriptor descriptor, @NotNull PlatformToKotlinClassMap platformToKotlinClassMap);

    void addAliasImport(@NotNull DeclarationDescriptor descriptor, @NotNull Name aliasName);

    Importer DO_NOTHING = new Importer() {
        @Override
        public void addAllUnderImport(@NotNull DeclarationDescriptor descriptor, @NotNull PlatformToKotlinClassMap platformToKotlinClassMap) {
        }

        @Override
        public void addAliasImport(@NotNull DeclarationDescriptor descriptor, @NotNull Name aliasName) {
        }
    };

    class StandardImporter implements Importer {
        private final WritableScope fileScope;

        public StandardImporter(WritableScope fileScope) {
            this.fileScope = fileScope;
        }

        @Override
        public void addAllUnderImport(@NotNull DeclarationDescriptor descriptor, @NotNull PlatformToKotlinClassMap platformToKotlinClassMap) {
            importAllUnderDeclaration(descriptor, platformToKotlinClassMap);
        }

        @Override
        public void addAliasImport(@NotNull DeclarationDescriptor descriptor, @NotNull Name aliasName) {
            importDeclarationAlias(descriptor, aliasName);
        }

        @NotNull
        private static JetScope createFilteringScope(
                @NotNull JetScope scope,
                @NotNull DeclarationDescriptor descriptor,
                @NotNull PlatformToKotlinClassMap platformToKotlinClassMap
        ) {
            final Collection<ClassDescriptor> kotlinAnalogsForClassesInside = platformToKotlinClassMap.mapPlatformClassesInside(descriptor);
            if (kotlinAnalogsForClassesInside.isEmpty()) return scope;
            return new FilteringScope(scope, new Function1<DeclarationDescriptor, Boolean>() {
                @Override
                public Boolean invoke(DeclarationDescriptor descriptor) {
                    for (ClassDescriptor kotlinAnalog : kotlinAnalogsForClassesInside) {
                        if (kotlinAnalog.getName().equals(descriptor.getName())) {
                            return false;
                        }
                    }
                    return true;
                }
            });
        }

        protected void importAllUnderDeclaration(@NotNull DeclarationDescriptor descriptor, @NotNull PlatformToKotlinClassMap platformToKotlinClassMap) {
            List<JetScope> scopesToImport = new ArrayList<JetScope>(3);
            if (descriptor instanceof PackageViewDescriptor) {
                scopesToImport.add(new NoSubpackagesInPackageScope((PackageViewDescriptor) descriptor));
            }
            else if (descriptor instanceof ClassDescriptor && ((ClassDescriptor) descriptor).getKind() != ClassKind.OBJECT) {
                ClassDescriptor classDescriptor = (ClassDescriptor) descriptor;
                scopesToImport.add(classDescriptor.getStaticScope());
                scopesToImport.add(classDescriptor.getUnsubstitutedInnerClassesScope());
                ClassDescriptor classObjectDescriptor = classDescriptor.getClassObjectDescriptor();
                if (classObjectDescriptor != null) {
                    scopesToImport.add(classObjectDescriptor.getUnsubstitutedInnerClassesScope());
                }
            }

            for (JetScope scope : scopesToImport) {
                fileScope.importScope(createFilteringScope(scope, descriptor, platformToKotlinClassMap));
            }
        }

        protected void importDeclarationAlias(@NotNull DeclarationDescriptor descriptor, @NotNull Name aliasName) {
            if (descriptor instanceof ClassifierDescriptor) {
                fileScope.importClassifierAlias(aliasName, (ClassifierDescriptor) descriptor);
            }
            else if (descriptor instanceof PackageViewDescriptor) {
                fileScope.importPackageAlias(aliasName, (PackageViewDescriptor) descriptor);
            }
            else if (descriptor instanceof FunctionDescriptor) {
                fileScope.importFunctionAlias(aliasName, (FunctionDescriptor) descriptor);
            }
            else if (descriptor instanceof VariableDescriptor) {
                fileScope.importVariableAlias(aliasName, (VariableDescriptor) descriptor);
            }
        }

    }

    class DelayedImporter extends StandardImporter {
        private interface DelayedImportEntry {}
        private static class AllUnderImportEntry extends Pair<DeclarationDescriptor, PlatformToKotlinClassMap> implements DelayedImportEntry {
            public AllUnderImportEntry(@NotNull DeclarationDescriptor first, @Nullable PlatformToKotlinClassMap second) {
                super(first, second);
            }
        }
        private static class AliasImportEntry extends Pair<DeclarationDescriptor, Name> implements DelayedImportEntry {
            public AliasImportEntry(DeclarationDescriptor first, Name second) {
                super(first, second);
            }
        }

        private final List<DelayedImportEntry> imports = Lists.newArrayList();

        public DelayedImporter(@NotNull WritableScope fileScope) {
            super(fileScope);
        }

        @Override
        public void addAllUnderImport(@NotNull DeclarationDescriptor descriptor, @NotNull PlatformToKotlinClassMap platformToKotlinClassMap) {
            imports.add(new AllUnderImportEntry(descriptor, platformToKotlinClassMap));
        }

        @Override
        public void addAliasImport(@NotNull DeclarationDescriptor descriptor, @NotNull Name aliasName) {
            imports.add(new AliasImportEntry(descriptor, aliasName));
        }

        public void processImports() {
            for (DelayedImportEntry anImport : imports) {
                if (anImport instanceof AllUnderImportEntry) {
                    AllUnderImportEntry allUnderImportEntry = (AllUnderImportEntry) anImport;
                    importAllUnderDeclaration(allUnderImportEntry.getFirst(), allUnderImportEntry.getSecond());
                }
                else {
                    AliasImportEntry aliasImportEntry = (AliasImportEntry) anImport;
                    importDeclarationAlias(aliasImportEntry.getFirst(), aliasImportEntry.getSecond());
                }
            }
        }
    }
}
