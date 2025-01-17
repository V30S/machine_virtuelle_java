package fr.umlv.smalljs.astinterp;

import fr.umlv.smalljs.ast.Expr;
import fr.umlv.smalljs.ast.Expr.*;
import fr.umlv.smalljs.ast.Script;
import fr.umlv.smalljs.rt.Failure;
import fr.umlv.smalljs.rt.JSObject;
import fr.umlv.smalljs.rt.JSObject.Invoker;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static fr.umlv.smalljs.rt.JSObject.UNDEFINED;

public final class ASTInterpreter {
    private static JSObject asJSObject(Object value, int lineNumber) {
        if (!(value instanceof JSObject jsObject)) {
            throw new Failure("at line " + lineNumber + ", type error " + value + " is not a JSObject");
        }
        return jsObject;
    }

    static Object visit(Expr expression, JSObject env) {
        return switch (expression) {
            case Block(List<Expr> instrs, int lineNumber) -> {
                //throw new UnsupportedOperationException("TODO Block");
                for (var instr : instrs) {
                    visit(instr, env);
                }
                yield UNDEFINED;
            }

            case Literal<?>(Object value, int lineNumber) -> value;

            case FunCall(Expr qualifier, List<Expr> args, int lineNumber) -> {
                var value = visit(qualifier, env);
                if (!(value instanceof JSObject jsObject)) {
                    throw new Failure("Not a function at line " + lineNumber);
                }

                var values = args.stream().map(arg -> visit(arg, env)).toArray();
                yield jsObject.invoke(UNDEFINED, values);
            }

            case LocalVarAccess(String name, int lineNumber) -> env.lookup(name);

            case LocalVarAssignment(String name, Expr expr, boolean declaration, int lineNumber) -> {
                if (declaration && env.lookup(name) != UNDEFINED) {
                    throw new Failure("Variable " + name + " already defined at line " + lineNumber);
                }
                var value = visit(expr, env);
                env.register(name, value);
                yield value;
            }

            case Fun(Optional<String> optName, List<String> parameters, Block body, int lineNumber) -> {

                Invoker invoker = new Invoker() {
                    @Override
                    public Object invoke(Object receiver, Object... args) {
                        // check the arguments length
                        if (args.length != parameters.size()) {
                            throw new Failure("Wrong number of arguments");
                        }

                        // create a new environment
                        var newEnv = JSObject.newEnv(env);

                        // add this and all the parameters
                        newEnv.register("this", receiver);

                        for (int i = 0; i < args.length; i++) {
                            var parameter = parameters.get(i);
                            var arg = args[i];
                            newEnv.register(parameter, arg);
                        }

                        // visit the body
                        try {
                            return visit(body, newEnv);
                        } catch (ReturnError returnError) {
                            return returnError.getValue();
                        }
                    }
                };

                // create the JS function with the invoker
                var function = JSObject.newFunction(optName.orElse("lambda"), invoker);

                // register it if necessary
                optName.ifPresent(name -> {
                    env.register(name, function);
                });

                yield function;
            }

            case Return(Expr expr, int lineNumber) -> throw new ReturnError(visit(expr, env));

            case If(Expr condition, Block trueBlock, Block falseBlock, int lineNumber) -> {
                var valueCondition = visit(condition, env);
                if (valueCondition instanceof Integer i && i != 0) {
                    visit(trueBlock, env);
                } else {
                    visit(falseBlock, env);
                }
                yield UNDEFINED;
            }

            case New(Map<String, Expr> initMap, int lineNumber) -> {
                throw new UnsupportedOperationException("TODO New");
            }
            case FieldAccess(Expr receiver, String name, int lineNumber) -> {
                throw new UnsupportedOperationException("TODO FieldAccess");
            }
            case FieldAssignment(Expr receiver, String name, Expr expr, int lineNumber) -> {
                throw new UnsupportedOperationException("TODO FieldAssignment");
            }
            case MethodCall(Expr receiver, String name, List<Expr> args, int lineNumber) -> {
                throw new UnsupportedOperationException("TODO MethodCall");
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static JSObject createGlobalEnv(PrintStream outStream) {
        JSObject globalEnv = JSObject.newEnv(null);
        globalEnv.register("global", globalEnv);
        globalEnv.register("print", JSObject.newFunction("print", (_, args) -> {
            System.err.println("print called with " + Arrays.toString(args));
            outStream.println(Arrays.stream(args).map(Object::toString).collect(Collectors.joining(" ")));
            return UNDEFINED;
        }));
        globalEnv.register("+", JSObject.newFunction("+", (_, args) -> (Integer) args[0] + (Integer) args[1]));
        globalEnv.register("-", JSObject.newFunction("-", (_, args) -> (Integer) args[0] - (Integer) args[1]));
        globalEnv.register("/", JSObject.newFunction("/", (_, args) -> (Integer) args[0] / (Integer) args[1]));
        globalEnv.register("*", JSObject.newFunction("*", (_, args) -> (Integer) args[0] * (Integer) args[1]));
        globalEnv.register("%", JSObject.newFunction("%", (_, args) -> (Integer) args[0] % (Integer) args[1]));
        globalEnv.register("==", JSObject.newFunction("==", (_, args) -> args[0].equals(args[1]) ? 1 : 0));
        globalEnv.register("!=", JSObject.newFunction("!=", (_, args) -> !args[0].equals(args[1]) ? 1 : 0));
        globalEnv.register("<", JSObject.newFunction("<", (_, args) -> (((Comparable<Object>) args[0]).compareTo(args[1]) < 0) ? 1 : 0));
        globalEnv.register("<=", JSObject.newFunction("<=", (_, args) -> (((Comparable<Object>) args[0]).compareTo(args[1]) <= 0) ? 1 : 0));
        globalEnv.register(">", JSObject.newFunction(">", (_, args) -> (((Comparable<Object>) args[0]).compareTo(args[1]) > 0) ? 1 : 0));
        globalEnv.register(">=", JSObject.newFunction(">=", (_, args) -> (((Comparable<Object>) args[0]).compareTo(args[1]) >= 0) ? 1 : 0));
        return globalEnv;
    }

    public static void interpret(Script script, PrintStream outStream) {
        JSObject globalEnv = createGlobalEnv(outStream);
        Block body = script.body();
        visit(body, globalEnv);
    }
}

