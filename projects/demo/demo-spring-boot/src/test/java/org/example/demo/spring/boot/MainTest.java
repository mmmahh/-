package org.example.demo.spring.boot;


import org.junit.jupiter.api.Test;

import java.lang.annotation.*;
import java.lang.reflect.TypeVariable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class MainTest {
    enum A {

    }

    enum B {

    }

    @Target(ElementType.TYPE_PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    @interface C {

    }

    interface D {

    }

    @Test
    public void test() {
        Class<int[]> c1 = int[].class;
        Class<Integer[]> c2 = Integer[].class;

        Object[] arr = new Integer[1];
        arr[0] = 3;



        System.out.println();
    }

    private static class Test2<@C D extends A> {

    }

    @Test
    public void test2() {
        TypeVariable<Class<Test2>>[] typeParameters = Test2.class.getTypeParameters();
        System.out.println();

    }

    @Test
    public void test3() throws ExecutionException, InterruptedException {
        CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(() -> {
            throw new NullPointerException();
            // System.out.println(Thread.currentThread().getName());
        });

        completableFuture.thenAccept(r -> System.out.println(r)).exceptionally(e -> {
            e.printStackTrace();
            return null;
        });

        completableFuture.whenComplete((r, e) -> {

        });
        completableFuture.handle((r, e) -> {
            return null;
        });
        completableFuture.exceptionally(e -> {
            return null;
        });




        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> "hello")
                .thenCompose(r -> CompletableFuture.supplyAsync(() -> r + " world"));

        CompletableFuture<Void> future1 = CompletableFuture.allOf(
                CompletableFuture.supplyAsync(() -> "hello"),
                CompletableFuture.supplyAsync(() -> " world"));
                // CompletableFuture.supplyAsync();


        // Thread.sleep(5000);
        System.out.println(future.get());
    }

    @Test
    public void test4() throws ExecutionException, InterruptedException {
        CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> {
                    throw new NullPointerException();
                })
                .thenRun(() -> System.out.println("hello"))
                .exceptionally(e -> {
                    e.printStackTrace();
                    return null;
                });

        Object o = future.get();
        // System.out.println(o);
        Thread.sleep(2000);
        System.out.println("end");
    }
}