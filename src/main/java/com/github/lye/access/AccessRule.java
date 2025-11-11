package com.github.lye.access;
public interface AccessRule {
  String id();
  Decision decide(AccessContext ctx, String itemKey);
}