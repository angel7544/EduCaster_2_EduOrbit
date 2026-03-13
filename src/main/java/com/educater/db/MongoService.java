package com.educater.db;

import com.educater.config.HardcodedConfig;
import com.educater.model.StreamRecord;
import com.educater.model.UploadRecord;
import com.educater.model.User;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;

public class MongoService implements AutoCloseable {
    private final MongoClient client;
    private final MongoDatabase db;
    private final MongoCollection<Document> users;
    private final MongoCollection<Document> streams;
    private final MongoCollection<Document> uploads;

    public MongoService() {
        String uri = HardcodedConfig.getOrEnv(HardcodedConfig.MONGO_URI, "MONGO_URI");
        String dbName = HardcodedConfig.getOrEnv(HardcodedConfig.MONGO_DB, "MONGO_DB");
        this.client = MongoClients.create(uri);
        this.db = client.getDatabase(dbName);
        this.users = db.getCollection("users");
        this.streams = db.getCollection("streams");
        this.uploads = db.getCollection("uploads");
        // indexes (best effort)
        this.users.createIndex(new Document("email", 1));
        this.streams.createIndex(new Document("email", 1));
        this.streams.createIndex(new Document("created_at", -1));
        this.uploads.createIndex(new Document("email", 1));
        this.uploads.createIndex(new Document("created_at", -1));
    }

    public boolean createUser(User u) {
        try {
            Document d = new Document("email", u.email)
                    .append("password_hash", u.passwordHashBase64)
                    .append("salt", u.saltBase64)
                    .append("created_at", Instant.now().toString());
            users.insertOne(d);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean updateUserPassword(String email, String passwordHashBase64, String saltBase64) {
        try {
            users.updateOne(eq("email", email), new Document("$set", new Document("password_hash", passwordHashBase64).append("salt", saltBase64)));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public User findUserByEmail(String email) {
        Document d = users.find(eq("email", email)).first();
        if (d == null) return null;
        User u = new User();
        u.id = d.getObjectId("_id");
        u.email = d.getString("email");
        u.passwordHashBase64 = d.getString("password_hash");
        u.saltBase64 = d.getString("salt");
        u.createdAtIso = d.getString("created_at");
        return u;
    }

    public void saveStream(String email, StreamRecord r) {
        Document d = new Document("email", email)
                .append("rtmp_url", r.rtmpUrl)
                .append("stream_key", r.streamKey)
                .append("playback_id", r.playbackId)
                .append("live_stream_id", r.liveStreamId)
                .append("created_at", Instant.now().toString());
        streams.insertOne(d);
    }

    public List<StreamRecord> getStreamsByEmail(String email) {
        List<StreamRecord> list = new ArrayList<>();
        for (Document d : streams.find(eq("email", email))) {
            StreamRecord r = new StreamRecord();
            r.id = d.getObjectId("_id");
            r.email = d.getString("email");
            r.rtmpUrl = d.getString("rtmp_url");
            r.streamKey = d.getString("stream_key");
            r.playbackId = d.getString("playback_id");
            r.liveStreamId = d.getString("live_stream_id");
            r.createdAtIso = d.getString("created_at");
            list.add(r);
        }
        return list;
    }

    public void deleteStreamById(ObjectId id) {
        streams.deleteOne(eq("_id", id));
    }

    public void saveUpload(String email, UploadRecord r) {
        Document d = new Document("email", email)
                .append("title", r.title)
                .append("policy", r.policy)
                .append("file_name", r.fileName)
                .append("asset_id", r.assetId)
                .append("playback_id", r.playbackId)
                .append("created_at", Instant.now().toString());
        uploads.insertOne(d);
    }

    public List<UploadRecord> getUploadsByEmail(String email) {
        List<UploadRecord> list = new ArrayList<>();
        for (Document d : uploads.find(eq("email", email))) {
            UploadRecord r = new UploadRecord();
            r.id = d.getObjectId("_id");
            r.email = d.getString("email");
            r.title = d.getString("title");
            r.policy = d.getString("policy");
            r.fileName = d.getString("file_name");
            r.assetId = d.getString("asset_id");
            r.playbackId = d.getString("playback_id");
            r.createdAtIso = d.getString("created_at");
            list.add(r);
        }
        return list;
    }

    @Override
    public void close() {
        client.close();
    }
}