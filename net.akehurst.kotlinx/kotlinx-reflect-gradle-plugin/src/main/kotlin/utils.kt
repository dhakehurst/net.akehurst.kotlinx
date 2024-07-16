package net.akehurst.kotlinx.reflect.gradle.plugin

import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.IrFunctionBuilder
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.declarations.IrAnonymousInitializer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.impl.IrAnonymousInitializerImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrAnonymousInitializerSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.util.defaultType

//inline fun IrClass.addAnonymousInitializer(builder: IrFunctionBuilder.() -> Unit = {}): IrAnonymousInitializer =
//    IrFunctionBuilder().run {
//        builder()
//        returnType = defaultType
//        IrAnonymousInitializerImpl(
//            startOffset, endOffset, origin,
//            IrAnonymousInitializerSymbolImpl()
//        )
//    }.also { anonymousInitializer ->
//        declarations.add(anonymousInitializer)
//        anonymousInitializer.parent = this@addAnonymousInitializer
//    }

//fun IrBuilderWithScope.kClassReference(classType: IrType) =
//    IrClassReferenceImpl(
//        startOffset, endOffset, context.irBuiltIns.kClassClass.starProjectedType, context.irBuiltIns.kClassClass, classType
//    )

fun IrBuilderWithScope.classReference(classSymbol: IrClassSymbol, type: IrType) =
    IrClassReferenceImpl(
        startOffset, endOffset, type, classSymbol, classSymbol.starProjectedType
    )