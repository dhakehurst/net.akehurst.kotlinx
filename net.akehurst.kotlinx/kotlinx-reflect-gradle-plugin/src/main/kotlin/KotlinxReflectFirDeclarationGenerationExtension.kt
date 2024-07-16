package net.akehurst.kotlinx.reflect.gradle.plugin

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.builder.buildBlock
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicate.AbstractPredicate
import org.jetbrains.kotlin.fir.plugin.createConeType
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.resolve.typeResolver
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

class KotlinxReflectFirDeclarationGenerationExtension(
    session: FirSession,
    private val  messageCollector: MessageCollector,
    private val  kotlinxReflectRegisterForModuleClassFqName: String,
    private val forReflection: List<String>
    ) : FirDeclarationGenerationExtension(session) {

//    private val messageCollector: MessageCollector = KotlinxReflectFirExtensionRegistrar.messageCollector
//    private val kotlinxReflectRegisterForModuleClassFqName: String = KotlinxReflectFirExtensionRegistrar.kotlinxReflectRegisterForModuleClassFqName
//    private val forReflection: List<String> = KotlinxReflectFirExtensionRegistrar.forReflection

    override fun generateFunctions(callableId: CallableId, context: MemberGenerationContext?): List<FirNamedFunctionSymbol> {
        messageCollector.report(CompilerMessageSeverity.LOGGING, "KotlinxReflect-FIR: generateFunctions")
        val owner = context?.owner ?: return emptyList()
        return if (owner.classId == ClassId.fromString(kotlinxReflectRegisterForModuleClassFqName.replace(".", "/"))) {

            forReflection.forEach { pkgName ->
                when {
                    pkgName.contains("*").not() -> {
                        val pkgFqName = FqName(pkgName)
                        messageCollector.report(CompilerMessageSeverity.LOGGING, "KotlinxReflect-FIR: Checking content of package '$pkgName'")


                        session.symbolProvider.symbolNamesProvider.getPackageNames()?.forEach { pn ->
                            session.symbolProvider.symbolNamesProvider.getTopLevelClassifierNamesInPackage(FqName(pn))?.forEach { cn ->
                                messageCollector.report(CompilerMessageSeverity.LOGGING, "KotlinxReflect-FIR: Checking content of class '$cn'")
                            }
                        }


//                        moduleFragment.descriptor.getPackage(pkgFqName).fragments.forEach { frag ->
//                            frag.getMemberScope().getClassifierNames()?.forEach { cls ->
//                                val sym = pluginContext.referenceClass(ClassId(frag.fqName, cls))
//                                if (null != sym) {// && true==sym.signature?.isPubliclyVisible) {
//                                    messageCollector.report(CompilerMessageSeverity.LOGGING, "KotlinxReflect: include = ${frag.fqName}.${cls}")
//                                    classesToRegisterForReflection.add(sym)
//                                    globRegexes[pkgName.toRegexFromGlob('.')] = Pair(pkgName, true)
//                                } else {
//                                    messageCollector.report(CompilerMessageSeverity.LOGGING, "KotlinxReflect: exclude = ${frag.fqName}.${cls}")
//                                }
//                            }
//                        }
                    }

                    else -> Unit // TODO
                }
            }

//            val function = createMemberFunction(owner, Key, callableId.callableName, session.builtinTypes.unitType.type) {
//                valueParameter(X_NAME, argumentClassId.createConeType(session))
//            }.apply {
//                replaceBody(buildBlock {}.apply { replaceConeTypeOrNull(session.builtinTypes.unitType.type) })
//            }
//            listOf(function.symbol)
            emptyList()
        } else {
            emptyList()
        }
    }

}