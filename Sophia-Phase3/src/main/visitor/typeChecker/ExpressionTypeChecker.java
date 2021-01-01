package main.visitor.typeChecker;

import com.sun.jdi.LocalVariable;
import main.ast.nodes.declaration.classDec.ClassDeclaration;
import main.ast.nodes.declaration.classDec.classMembersDec.ConstructorDeclaration;
import main.ast.nodes.declaration.classDec.classMembersDec.FieldDeclaration;
import main.ast.nodes.declaration.classDec.classMembersDec.MethodDeclaration;
import main.ast.nodes.declaration.variableDec.VarDeclaration;
import main.ast.nodes.expression.*;
import main.ast.nodes.expression.operators.BinaryOperator;
import main.ast.nodes.expression.operators.UnaryOperator;
import main.ast.nodes.expression.values.ListValue;
import main.ast.nodes.expression.values.NullValue;
import main.ast.nodes.expression.values.Value;
import main.ast.nodes.expression.values.primitive.BoolValue;
import main.ast.nodes.expression.values.primitive.IntValue;
import main.ast.nodes.expression.values.primitive.StringValue;
import main.ast.types.NoType;
import main.visitor.typeChecker.LValueTypeChecker;
import main.ast.types.NullType;
import main.ast.types.Type;
import main.ast.types.list.ListNameType;
import main.ast.types.list.ListType;
import main.ast.types.single.BoolType;
import main.ast.types.single.ClassType;
import main.ast.types.single.IntType;
import main.ast.types.functionPointer.FptrType;
import main.ast.types.single.StringType;
import main.symbolTable.exceptions.ItemNotFoundException;
import main.symbolTable.utils.graph.Graph;
import main.symbolTable.utils.graph.exceptions.GraphDoesNotContainNodeException;
import main.visitor.Visitor;
import main.symbolTable.SymbolTable;
import main.symbolTable.items.*;
import main.compileErrorException.typeErrors.*;
 /*
 *1-varNotDeclared
 *2-ClassNotDeclared
 *3-check if member exists
 *4-check operand types for operators
 6-left side must be lvalue not rvalue (sina)
 *7-lvalue boodan operator haye ++ and --
 *8-cant call not callable -------> dont check 13,15
 *12-multi type list index must be integerVal mostaghiman
 *15-args in method call must match definition
 *16-constructor args must match definition(check in new classInstance)
 18-list elements cant have same ids (maybe Sina's job)
 *22-cant index non list objects
 *23-list index must be integer
 *24-id must exist in list
 *30-object or list member access on a expression that is neither a list not object
  */
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;


public class ExpressionTypeChecker extends Visitor<Type> {
    private final Graph<String> classHierarchy;
    private ClassDeclaration currentClass;
    private MethodDeclaration currentMethod;
    private boolean checkingMemberAccess = false;
    private ClassType classCheckingMemberFor;
    public boolean methodCallStatement = false;
    private boolean isExpressionLValue = true;
    public boolean isLValueVisitor = false;

    public void setCurrentClass(ClassDeclaration classDec){ this.currentClass = classDec;}

    public void setCurrentMethod(MethodDeclaration methodDec){ this.currentMethod = methodDec;}

    public ClassDeclaration getCurrentClass(){ return this.currentClass;}

    public MethodDeclaration getCurrentMethod(){ return this.currentMethod;}

    public ExpressionTypeChecker(Graph<String> classHierarchy) {
        this.classHierarchy = classHierarchy;
    }

    @Override
    public Type visit(BinaryExpression binaryExpression) {
        this.isExpressionLValue = false;
        Type type1st = binaryExpression.getFirstOperand().accept(this);
        Type type2nd = binaryExpression.getSecondOperand().accept(this);
        BinaryOperator op = binaryExpression.getBinaryOperator();
        if (op == BinaryOperator.eq || op == BinaryOperator.neq){
            if (type1st instanceof ListType || type2nd instanceof ListType){
                if(!this.isLValueVisitor)
                    binaryExpression.addError(new UnsupportedOperandType(binaryExpression.getLine(), op.name()));
                return new NoType();
            }
            if (type1st instanceof NoType || type2nd instanceof NoType){
                return new NoType();
            }
            if (this.canTypesBeCompared(type1st, type2nd)){
                return new BoolType();
            }else{
                if(!this.isLValueVisitor)
                    binaryExpression.addError(new UnsupportedOperandType(binaryExpression.getLine(), op.name()));
            }
        }else if (op == BinaryOperator.and || op == BinaryOperator.or){
            if (type1st instanceof BoolType){
                if (type2nd instanceof BoolType){
                    return new BoolType();
                }else if (type2nd instanceof NoType){
                    return new NoType();
                }
                if(!this.isLValueVisitor)
                    binaryExpression.addError(new UnsupportedOperandType(binaryExpression.getLine(), op.name()));
                return new NoType();
            }else if (type1st instanceof NoType){
                if (!(type2nd instanceof BoolType || type2nd instanceof NoType))
                    if(!this.isLValueVisitor)
                        binaryExpression.addError(new UnsupportedOperandType(binaryExpression.getLine(), op.name()));
                return new NoType();
            }
            if(!this.isLValueVisitor)
                binaryExpression.addError(new UnsupportedOperandType(binaryExpression.getLine(), op.name()));
            return new NoType();
        }else if (op == BinaryOperator.lt || op == BinaryOperator.gt) {
            if (type1st instanceof IntType) {
                if (type2nd instanceof IntType) {
                    return new BoolType();
                } else if (type2nd instanceof NoType) {
                    return new NoType();
                }
                if (!this.isLValueVisitor)
                    binaryExpression.addError(new UnsupportedOperandType(binaryExpression.getLine(), op.name()));
                return new NoType();
            } else if (type1st instanceof NoType) {
                if (!(type2nd instanceof IntType || type2nd instanceof NoType))
                    if (!this.isLValueVisitor)
                        binaryExpression.addError(new UnsupportedOperandType(binaryExpression.getLine(), op.name()));
                return new NoType();
            }
            if (!this.isLValueVisitor)
                binaryExpression.addError(new UnsupportedOperandType(binaryExpression.getLine(), op.name()));
            return new NoType();
        }else if(op == BinaryOperator.assign) {
            if(!this.islValue(binaryExpression.getFirstOperand())) {
                LeftSideNotLvalue leftSideNotLvalue = new LeftSideNotLvalue(binaryExpression.getLine()); // Error 6
                binaryExpression.addError(leftSideNotLvalue);
                return  new NoType();
            }
            if(!this.isSecondSubtypeOfFirst(type1st, type2nd)) {
                UnsupportedOperandType unsupportedOperandType =
                        new UnsupportedOperandType(binaryExpression.getLine(), BinaryOperator.assign.name()); // Error 4
                binaryExpression.addError(unsupportedOperandType);
                return  new NoType();
            }
            return type1st;
        }else{
            if (type1st instanceof IntType){
                if (type2nd instanceof IntType){
                    return new IntType();
                }else if (type2nd instanceof NoType){
                    return new NoType();
                }
                if(!this.isLValueVisitor)
                    binaryExpression.addError(new UnsupportedOperandType(binaryExpression.getLine(), op.name()));
                return new NoType();
            }else if (type1st instanceof NoType){
                if (!(type2nd instanceof IntType || type2nd instanceof NoType))
                    if(!this.isLValueVisitor)
                        binaryExpression.addError(new UnsupportedOperandType(binaryExpression.getLine(), op.name()));
                return new NoType();
            }
            if(!this.isLValueVisitor)
                binaryExpression.addError(new UnsupportedOperandType(binaryExpression.getLine(), op.name()));
            return new NoType();
        }
        return null;
    }

    @Override
    public Type visit(UnaryExpression unaryExpression) {
        this.isExpressionLValue = false;
        Type operandType = unaryExpression.getOperand().accept(this);
        UnaryOperator op = unaryExpression.getOperator();
        if (op == UnaryOperator.not){
            if (operandType instanceof BoolType){
                return new BoolType();
            }else if (operandType instanceof NoType){
                return new NoType();
            }
            if(!this.isLValueVisitor)
                unaryExpression.addError(new UnsupportedOperandType(unaryExpression.getLine(),
                    unaryExpression.getOperator().name()));
            return new NoType();
        }else if (op == UnaryOperator.minus){
            if (operandType instanceof IntType){
                return new IntType();
            }else if (operandType instanceof NoType){
                return new NoType();
            }
            if(!this.isLValueVisitor)
                unaryExpression.addError(new UnsupportedOperandType(unaryExpression.getLine(),
                    unaryExpression.getOperator().name()));
            return new NoType();
        }else{
            boolean flag = true;
            if (!(this.islValue(unaryExpression.getOperand()))){
                if(!this.isLValueVisitor)
                    unaryExpression.addError(new IncDecOperandNotLvalue(unaryExpression.getLine(),
                        unaryExpression.getOperator().name()));
                flag = false;
            }
            if (operandType instanceof IntType){
                if (!flag) return new NoType();
                return operandType;
            }else if (operandType instanceof NoType){
                return new NoType();
            }
            if(!this.isLValueVisitor)
             unaryExpression.addError(new UnsupportedOperandType(unaryExpression.getLine(),
                    unaryExpression.getOperator().name()));
            return new NoType();
        }
    }

    @Override
    public Type visit(ObjectOrListMemberAccess objectOrListMemberAccess) {
        Type instanceType = objectOrListMemberAccess.getInstance().accept(this);
        if (!(instanceType instanceof ClassType || instanceType instanceof ListType || instanceType instanceof NoType)){
            if(!this.isLValueVisitor)
                objectOrListMemberAccess.addError(new MemberAccessOnNoneObjOrListType(objectOrListMemberAccess.getLine()));
            return new NoType();
        }
        if (instanceType instanceof NoType){
            return new NoType();
        }
        if (instanceType instanceof ClassType){
            this.checkingMemberAccess = true;
            this.classCheckingMemberFor = (ClassType) instanceType;
            Type type = objectOrListMemberAccess.getMemberName().accept(this);
            if (type instanceof FptrType){
                this.isExpressionLValue = false;
            }
            this.checkingMemberAccess = false;
            return type;
        }else{
            ListType list = (ListType)instanceType;
            for (ListNameType listNameType : list.getElementsTypes()){
                if (listNameType.getName().getName().equals(objectOrListMemberAccess.getMemberName().getName())){
                    return listNameType.getType();
                }
            }
            if(!this.isLValueVisitor)
                objectOrListMemberAccess.addError(new ListMemberNotFound(objectOrListMemberAccess.getLine(),
                    objectOrListMemberAccess.getMemberName().getName()));
            return new NoType();
        }
    }

    @Override
    public Type visit(Identifier identifier) {
        if (this.checkingMemberAccess){
            //checking memberAccess
            Type type = this.doesIdentifierExistInClass(identifier.getName(), this.classCheckingMemberFor);
            if (type == null){
                if(!this.isLValueVisitor)
                    identifier.addError(new MemberNotAvailableInClass(identifier.getLine(), identifier.getName(),
                        this.classCheckingMemberFor.getClassName().getName()));
                return new NoType();
            }else{
                return type;
            }
        }else{
            Type type = this.doesLocalVarExist(identifier.getName());
            if (type == null){
                if(!this.isLValueVisitor)
                    identifier.addError(new VarNotDeclared(identifier.getLine(), identifier.getName()));
                return new NoType();
            }else{
                return type;
            }
        }
    }

    public boolean isListSingleType(ListType listType){
        ArrayList<ListNameType> types = listType.getElementsTypes();
        ListNameType last_type = null;
        for (ListNameType type : types){
            if (last_type == null){
                last_type = type;
            }
            if (!this.isSecondSubtypeOfFirst(last_type.getType(),type.getType())){
                return false;
            }
        }
        return true;
    }

    @Override
    public Type visit(ListAccessByIndex listAccessByIndex) {
        Type indexType = listAccessByIndex.getIndex().accept(this);
        Type instanceType = listAccessByIndex.getInstance().accept(this);
        if (!(instanceType instanceof ListType || instanceType instanceof NoType))
            if(!this.isLValueVisitor)
                listAccessByIndex.addError(new ListAccessByIndexOnNoneList(listAccessByIndex.getLine()));
        if (indexType instanceof IntType || indexType instanceof NoType){
            if (instanceType instanceof ListType || instanceType instanceof  NoType){
                if (instanceType instanceof NoType || indexType instanceof NoType){
                    return new NoType();
                }
                ListType castedIns = (ListType) instanceType;
                if (isListSingleType(castedIns)){
                    return (castedIns.getElementsTypes().get(0).getType());
                }else{
                    if (listAccessByIndex.getIndex() instanceof IntValue){
                        int ind = ((IntValue) listAccessByIndex.getIndex()).getConstant();
                        if (ind < castedIns.getElementsTypes().size()){
                            return (castedIns.getElementsTypes().get(ind).getType());
                        }else{
                            return (castedIns.getElementsTypes().get(0).getType());
                        }
                    }else{
                        if(!this.isLValueVisitor)
                            listAccessByIndex.addError(new CantUseExprAsIndexOfMultiTypeList(listAccessByIndex.getLine()));
                        return new NoType();
                    }
                }
            }
        }
        if (!(indexType instanceof IntType || indexType instanceof NoType))
            if(!this.isLValueVisitor)
                listAccessByIndex.addError(new ListIndexNotInt(listAccessByIndex.getLine()));
        return new NoType();
    }

    @Override
    public Type visit(MethodCall methodCall) {
        this.isExpressionLValue = false;
        Type insType = methodCall.getInstance().accept(this);
        ArrayList<Expression> args = methodCall.getArgs();
        if (insType instanceof FptrType){
            boolean flag = false;
            FptrType castedInsType = (FptrType)insType;
            if (castedInsType.getReturnType() instanceof NullType && !this.methodCallStatement){
                if(!this.isLValueVisitor)
                    methodCall.addError(new CantUseValueOfVoidMethod(methodCall.getLine())); //error 13
                flag = true;
            }
            ArrayList<Type> argTypes = castedInsType.getArgumentsTypes();
            //check if count is correct
            if (argTypes.size() != args.size()){
                if(!this.isLValueVisitor)
                    methodCall.addError(new MethodCallNotMatchDefinition(methodCall.getLine()));
                return new NoType();
            }
            for (int i = 0 ; i < argTypes.size() ; i++){
                Type argType = args.get(i).accept(this);
                if (!this.isSecondSubtypeOfFirst(argTypes.get(i), argType)){
                    if(!this.isLValueVisitor)
                        methodCall.addError(new MethodCallNotMatchDefinition(methodCall.getLine()));
                    return new NoType();
                }
            }
            if (flag == true)
                return new NoType();
            return castedInsType.getReturnType();
        }else if(insType instanceof NoType){
            return new NoType();
        }else{
            if(!this.isLValueVisitor)
                methodCall.addError(new CallOnNoneFptrType(methodCall.getLine()));
            return new NoType();
        }
    }

    @Override
    public Type visit(NewClassInstance newClassInstance) {
        this.isExpressionLValue = false;
        ClassType classType = newClassInstance.getClassType();
        try {
            ClassSymbolTableItem classSymbolTableItem = (ClassSymbolTableItem) SymbolTable.root.getItem
                    (ClassSymbolTableItem.START_KEY + classType.getClassName().getName(), true);
            ConstructorDeclaration classConstructor = classSymbolTableItem.getClassDeclaration().getConstructor();
            ArrayList<Expression> args = newClassInstance.getArgs();
            if(classConstructor == null) {
                if(args.size() != 0) {
                    if(!this.isLValueVisitor)
                        newClassInstance.addError(new ConstructorArgsNotMatchDefinition(newClassInstance));
                }
            } else{
                ArrayList<VarDeclaration> constructorArgs = classConstructor.getArgs();
                if (constructorArgs.size() != args.size()){
                    if(!this.isLValueVisitor)
                        newClassInstance.addError(new ConstructorArgsNotMatchDefinition(newClassInstance));
                    return new NoType();
                }
                for (int i = 0 ; i < args.size() ; i++){
                    Type argType = args.get(i).accept(this);
                    Type classArgType = constructorArgs.get(i).getType();
                    if (!this.isSecondSubtypeOfFirst(classArgType, argType)){
                        if(!this.isLValueVisitor)
                            newClassInstance.addError(new ConstructorArgsNotMatchDefinition(newClassInstance));
                        return new NoType();
                    }
                }
            }
        } catch (ItemNotFoundException e) {
            if(!this.isLValueVisitor)
                newClassInstance.addError(new ClassNotDeclared(newClassInstance.getLine(),
                    newClassInstance.getClassType().getClassName().getName()));
            return new NoType();
        }

        return newClassInstance.getClassType();
    }

    @Override
    public Type visit(ThisClass thisClass) {
        return new ClassType(this.getCurrentClass().getClassName());
    }

    @Override
    public Type visit(ListValue listValue) {
        this.isExpressionLValue = false;
        ArrayList<ListNameType> elementsTypes = new ArrayList<>();
        for (Expression exp : listValue.getElements()){
            Type type = exp.accept(this);
            elementsTypes.add(new ListNameType(type));
        }
        return new ListType(elementsTypes);
    }

    @Override
    public Type visit(NullValue nullValue) {
        this.isExpressionLValue = false;
        return new NullType();
    }

    @Override
    public Type visit(IntValue intValue) {
        this.isExpressionLValue = false;
        return new IntType();
    }

    @Override
    public Type visit(BoolValue boolValue) {
        this.isExpressionLValue = false;
        return new BoolType();
    }

    @Override
    public Type visit(StringValue stringValue) {
        this.isExpressionLValue = false;
        return new StringType();
    }

    public Boolean isSecondSubtypeOfFirst(Type first, Type second){
        if (second instanceof NoType || first instanceof NoType){
            return true;
        }
        //return type is void
        if (first instanceof NullType){
            return second instanceof NullType;
        }
        if (first instanceof FptrType || first instanceof ClassType){
            if (second instanceof NullType) return true;
            if (!first.getClass().equals(second.getClass())) return false;
            if (first instanceof ClassType){
                //classType
                if (((ClassType) first).getClassName().getName().equals(((ClassType) second).getClassName().getName())){
                    return true;
                }
                try {
                    Collection<String> parents =
                            this.classHierarchy.getParentsOfNode(((ClassType) second).getClassName().getName());
                    return parents.contains(((ClassType) first).getClassName().getName());
                } catch (GraphDoesNotContainNodeException e) { return false; }
            }else{
                //functionPtr
                FptrType castedFirst = (FptrType) first;
                FptrType castedSecond = (FptrType) second;
                if (!this.isSecondSubtypeOfFirst(castedFirst.getReturnType(),castedSecond.getReturnType())){
                    return false;
                }
                for (int i = 0 ; i < castedFirst.getArgumentsTypes().size() ; i++){
                    Type arg1 = castedFirst.getArgumentsTypes().get(i);
                    Type arg2 = castedSecond.getArgumentsTypes().get(i);
                    if (!this.isSecondSubtypeOfFirst(arg1,arg2)){
                        return false;
                    }
                }
                return true;
            }
        }else if (first instanceof ListType){
            //ListType
            if (!first.getClass().equals(second.getClass())) return false;
            ListType lis1 = (ListType)first;
            ListType lis2 = (ListType)second;
            if (lis1.getElementsTypes().size() != lis2.getElementsTypes().size()) return false;
            for (int i = 0 ; i < lis1.getElementsTypes().size() ; i++){
                if (!lis1.getElementsTypes().get(i).getClass().equals(lis2.getElementsTypes().get(i).getClass()))
                    return false;
            }
        }else{
            //OtherTypes
            return first.getClass().equals(second.getClass());
        }
        return true;
    }

    private FptrType getFunctionPointer(MethodDeclaration methodDec){
        ArrayList<VarDeclaration> args = methodDec.getArgs();
        ArrayList<Type> types = new ArrayList<>();
        for (VarDeclaration arg : args){
            types.add(arg.getType());
        }
        return new FptrType(types, methodDec.getReturnType());
    }

    private Type doesLocalVarExist(String identifierName){
        //is in method?
        for (VarDeclaration methodArg : this.getCurrentMethod().getLocalVars()) {
            if (identifierName.equals(methodArg.getVarName().getName())) {
                return methodArg.getType();
            }
        }
        for (VarDeclaration methodArg : this.getCurrentMethod().getArgs()) {
            if (identifierName.equals(methodArg.getVarName().getName())) {
                return methodArg.getType();
            }
        }
        return null;
    }

    private Type doesIdentifierExistInClass(String identifierName, ClassType classType){
        try {
            ClassSymbolTableItem checkClassSymbolTableItem = (ClassSymbolTableItem)
                    SymbolTable.root.getItem(ClassSymbolTableItem.START_KEY + classType.getClassName().getName(), true);
            ClassDeclaration classDec = checkClassSymbolTableItem.getClassDeclaration();
            //is in Class?
            ArrayList<FieldDeclaration> classArgs = classDec.getFields();
            for (FieldDeclaration classArg : classArgs) {
                if (identifierName.equals(classArg.getVarDeclaration().getVarName().getName())) {
                    return classArg.getVarDeclaration().getType();
                }
            }
            ArrayList<MethodDeclaration> classMethods = classDec.getMethods();
            for (MethodDeclaration classMethod : classMethods) {
                if (identifierName.equals(classMethod.getMethodName().getName())) {
                    return getFunctionPointer(classMethod);
                }
            }
            if(classDec.getConstructor() != null) {
                if(identifierName.equals(classDec.getConstructor().getMethodName().getName())) {
                    return getFunctionPointer((MethodDeclaration) classDec.getConstructor());
                }
            }
            //is in parents?
            try {
                Collection<String> parentNames
                        = classHierarchy.getParentsOfNode(classDec.getClassName().getName());
                // TODO: Add function
                for (String name : parentNames){
                    try {
                        ClassSymbolTableItem classSymbolTableItem = (ClassSymbolTableItem)
                                SymbolTable.root.getItem(ClassSymbolTableItem.START_KEY + name, true);
                        ArrayList<FieldDeclaration> classFields = classSymbolTableItem.getClassDeclaration().getFields();
                        for (FieldDeclaration classField : classFields) {
                            if (identifierName.equals(classField.getVarDeclaration().getVarName().getName())) {
                                return classField.getVarDeclaration().getType();
                            }
                        }
                        ArrayList<MethodDeclaration> methods = classSymbolTableItem.getClassDeclaration().getMethods();
                        for (MethodDeclaration method : methods) {
                            if (identifierName.equals(method.getMethodName().getName())) {
                                return getFunctionPointer(method);
                            }
                        }
                        if(classSymbolTableItem.getClassDeclaration().getConstructor() != null) {
                            if(identifierName.equals(classSymbolTableItem.getClassDeclaration().getConstructor().getMethodName().getName())) {
                                return getFunctionPointer((MethodDeclaration) classSymbolTableItem.getClassDeclaration().getConstructor());
                            }
                        }
                    } catch (ItemNotFoundException ignored) { }
                }
            } catch (GraphDoesNotContainNodeException e) { return null; }
        } catch (ItemNotFoundException e) { return null; }
        return null;
    }

    public Boolean islValue(Expression expression){
        //        LValueTypeChecker visit = new LValueTypeChecker();
        this.isLValueVisitor = true;
        this.isExpressionLValue = true;
        expression.accept(this);
        this.isLValueVisitor = false;
        return this.isExpressionLValue;
    }

    private Boolean canTypesBeCompared(Type first, Type second){
        if ((first instanceof FptrType || first instanceof ClassType || first instanceof NullType)){
            if (first instanceof NullType){
                return second instanceof FptrType || second instanceof ClassType || second instanceof NullType;
            }
            if (first instanceof ClassType){
                //classType
                if (second instanceof NullType) return true;
                if (!(second instanceof ClassType)) return false;
                return ((ClassType) first).getClassName().getName().equals(((ClassType) second).getClassName().getName());
            }else{
                //functionPtr
                if (second instanceof NullType) return true;
                if (!(second instanceof FptrType)) return false;
                FptrType castedFirst = (FptrType) first;
                FptrType castedSecond = (FptrType) second;
                if (!this.isSecondSubtypeOfFirst(castedFirst.getReturnType(),castedSecond.getReturnType())){
                    return false;
                }
                for (int i = 0 ; i < castedFirst.getArgumentsTypes().size() ; i++) {
                    Type arg1 = castedFirst.getArgumentsTypes().get(i);
                    Type arg2 = castedSecond.getArgumentsTypes().get(i);
                    if (!this.isSecondSubtypeOfFirst(arg1,arg2)){
                        return false;
                    }
                }
                return true;
            }
        }else if (first instanceof ListType) {
            return false;
        }else{
            //OtherTypes
            return first.getClass().equals(second.getClass());
        }
    }

    public Boolean doesClassExist(ClassType classType) {
        try {
            ClassSymbolTableItem classSymbolTableItem = (ClassSymbolTableItem) SymbolTable.root.getItem
                    (ClassSymbolTableItem.START_KEY + classType.getClassName().getName(), true);
            return true;
        } catch (ItemNotFoundException itemNotFoundException) {
            return false;
        }
    }
}