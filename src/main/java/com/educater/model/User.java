package com.educater.model;

import org.bson.types.ObjectId;

public class User {
    public ObjectId id;
    public String email;
    public String passwordHashBase64;
    public String saltBase64;
    public String createdAtIso;
}