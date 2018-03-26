/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.oracle.truffle.js.runtime.builtins;

import java.util.Arrays;
import java.util.EnumSet;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectFactory;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.object.LocationModifier;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.js.runtime.JSArguments;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.JavaScriptRootNode;
import com.oracle.truffle.js.runtime.Symbol;
import com.oracle.truffle.js.runtime.builtins.SIMDType.SIMDTypeFactory;
import com.oracle.truffle.js.runtime.objects.JSAttributes;
import com.oracle.truffle.js.runtime.objects.JSObject;
import com.oracle.truffle.js.runtime.objects.JSObjectUtil;
import com.oracle.truffle.js.runtime.objects.JSShape;
import com.oracle.truffle.js.runtime.objects.Null;
import com.oracle.truffle.js.runtime.objects.Undefined;

public final class JSSIMD extends JSBuiltinObject {
    public static final String SIMD_OBJECT_NAME = "SIMD";
    public static final String CLASS_NAME = "SIMDTypes";
    public static final String PROTOTYPE_NAME = "SIMDTypes.prototype";
    public static final String SIMD_TYPES_CLASS_NAME = "SIMDTypes";
    public static final JSSIMD INSTANCE = new JSSIMD();
    private static final HiddenKey SIMD_TYPE_ID = new HiddenKey("simdtype");
    private static final HiddenKey ARRAY_ID = new HiddenKey("simdarray");
    private static final Property SIMD_TYPE_PROPERTY;
    private static final Property ARRAY_PROPERTY;

    static {
        Shape.Allocator allocator = JSShape.makeAllocator(JSObject.LAYOUT);
        SIMD_TYPE_PROPERTY = JSObjectUtil.makeHiddenProperty(SIMD_TYPE_ID, allocator.locationForType(SIMDType.class, EnumSet.of(LocationModifier.NonNull)));
        ARRAY_PROPERTY = JSObjectUtil.makeHiddenProperty(ARRAY_ID, allocator.locationForType(Object.class, EnumSet.of(LocationModifier.NonNull)));
    }

    private JSSIMD() {
    }

    @Override
    public String getClassName(DynamicObject obj) {
        return CLASS_NAME;
    }

    public static SIMDType simdTypeGetSIMDType(DynamicObject thisObj) {
        return simdTypeGetSIMDType(thisObj, JSSIMD.isJSSIMD(thisObj));
    }

    public static SIMDType simdTypeGetSIMDType(DynamicObject thisObj, boolean condition) {
        return (SIMDType) SIMD_TYPE_PROPERTY.get(thisObj, condition);
    }

    static String simdTypeGetName(DynamicObject thisObj) {
        return simdTypeGetSIMDType(thisObj).getFactory().getName();
    }

    public static Object simdGetArray(DynamicObject thisObj, boolean arrayCondition) {
        assert JSSIMD.isJSSIMD(thisObj);
        return ARRAY_PROPERTY.get(thisObj, arrayCondition);
    }

    public static void simdSetArray(DynamicObject thisObj, Object array) {
        assert JSSIMD.isJSSIMD(thisObj);
        assert array != null && array.getClass().isArray();
        ARRAY_PROPERTY.setSafe(thisObj, array, null);
    }

    private static DynamicObject createTypedArrayPrototype(final JSRealm realm, DynamicObject ctor) {
        JSContext ctx = realm.getContext();
        DynamicObject prototype = JSObject.create(realm, realm.getObjectPrototype(), JSUserObject.INSTANCE);
        JSObjectUtil.putConstructorProperty(ctx, prototype, ctor);
        JSObjectUtil.putFunctionsFromContainer(realm, prototype, PROTOTYPE_NAME);

        DynamicObject toStringTagGetter = JSFunction.create(realm, JSFunctionData.createCallOnly(ctx, Truffle.getRuntime().createCallTarget(new JavaScriptRootNode(ctx.getLanguage(), null, null) {
            @Override
            public Object execute(VirtualFrame frame) {
                Object obj = JSArguments.getThisObject(frame.getArguments());
                if (JSObject.isDynamicObject(obj)) {
                    DynamicObject view = JSObject.castJSObject(obj);
                    if (isJSSIMD(view)) {
                        return simdTypeGetName(view);
                    }
                }
                return Undefined.instance;
            }
        }), 0, "get [Symbol.toStringTag]"));
        JSObjectUtil.putConstantAccessorProperty(ctx, prototype, Symbol.SYMBOL_TO_STRING_TAG, toStringTagGetter, Undefined.instance, JSAttributes.configurableNotEnumerable());
        return prototype;
    }

    public static JSConstructor createSIMDTypeConstructor(JSRealm realm) {
        JSContext ctx = realm.getContext();
        DynamicObject taConstructor = realm.lookupFunction(SIMD_OBJECT_NAME, SIMD_TYPES_CLASS_NAME);
        DynamicObject taPrototype = createTypedArrayPrototype(realm, taConstructor);
        JSObjectUtil.putDataProperty(ctx, taConstructor, JSObject.PROTOTYPE, taPrototype, JSAttributes.notConfigurableNotEnumerableNotWritable());

        JSObjectUtil.putConstantAccessorProperty(ctx, taConstructor, Symbol.SYMBOL_SPECIES, createSymbolSpeciesGetterFunction(realm), Undefined.instance,
                        JSAttributes.configurableNotEnumerableNotWritable());
        return new JSConstructor(taConstructor, taPrototype);
    }

    private static boolean isJSSIMD(DynamicObject obj) {
        return isInstance(obj, INSTANCE);
    }

    public static boolean isJSSIMD(Object obj) {
        return JSObject.isDynamicObject(obj) && isJSSIMD((DynamicObject) obj);
    }

    public static DynamicObject createSIMD(JSContext context, SIMDType simdType) {
        DynamicObjectFactory objectFactory = context.getSIMDTypeFactory(simdType.getFactory());
        Object[] values = new Object[simdType.getNumberOfElements()];
        Arrays.fill(values, Null.instance);
        DynamicObject simdObject = JSObject.create(context, objectFactory, simdType, values);
        return simdObject;
    }

    public static JSConstructor createConstructor(JSRealm realm, SIMDTypeFactory<? extends SIMDType> factory, JSConstructor taConstructor) {
        JSContext ctx = realm.getContext();
        String constructorName = factory.getName();
        DynamicObject simdConstructor = realm.lookupFunction(SIMD_OBJECT_NAME, constructorName);
        JSObject.setPrototype(simdConstructor, taConstructor.getFunctionObject());

        DynamicObject simdPrototype = createSIMDPrototype(realm, simdConstructor, factory, taConstructor.getPrototype());
        JSObjectUtil.putDataProperty(ctx, simdConstructor, JSObject.PROTOTYPE, simdPrototype, JSAttributes.notConfigurableNotEnumerableNotWritable());
        JSObjectUtil.putFunctionsFromContainer(realm, simdConstructor, constructorName);
        JSObjectUtil.putConstantAccessorProperty(ctx, simdConstructor, Symbol.SYMBOL_SPECIES, createSymbolSpeciesGetterFunction(realm), Undefined.instance,
                        JSAttributes.configurableNotEnumerableNotWritable());
        return new JSConstructor(simdConstructor, simdPrototype);
    }

    private static DynamicObject createSIMDPrototype(JSRealm realm, DynamicObject ctor, SIMDTypeFactory<? extends SIMDType> factory,
                    DynamicObject taPrototype) {
        JSContext context = realm.getContext();
        DynamicObject prototype = JSObject.create(realm, taPrototype, JSUserObject.INSTANCE);
        JSObjectUtil.putHiddenProperty(prototype, SIMD_TYPE_PROPERTY, factory.createSimdType());
        JSObjectUtil.putHiddenProperty(prototype, ARRAY_PROPERTY, new Object[0]);
        JSObjectUtil.putConstructorProperty(context, prototype, ctor);
        return prototype;
    }

    public static Shape makeInitialSIMDShape(JSContext context, DynamicObject prototype) {
        Shape childTree = JSObjectUtil.getProtoChildShape(prototype, INSTANCE, context);
        childTree = childTree.addProperty(SIMD_TYPE_PROPERTY);
        childTree = childTree.addProperty(ARRAY_PROPERTY);
        return childTree;
    }
}
