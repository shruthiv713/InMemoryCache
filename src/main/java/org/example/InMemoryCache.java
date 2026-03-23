package org.example;

import com.sun.source.tree.Tree;

import java.util.*;

public class InMemoryCache {
    private Map<String, TreeMap<String, FieldValue>> db = new HashMap<>();
    static class FieldValue {
         String value;
         long expiry;
         TreeMap<Long, String> versions;
         FieldValue() {
             versions = new TreeMap<>();
             this.expiry = Long.MAX_VALUE;
         }
         String getAt(long timestamp) {
             Map.Entry<Long, String> entry = versions.floorEntry(timestamp);
             if(entry==null) {
                 return null;
             }
             return entry.getValue().equals("\0")?null:entry.getValue();
         }
         boolean isExpired(long timestamp) {
             return expiry<=timestamp;
         }
    }
    public void set(long timestamp, String key, String field, String value) {

        db.computeIfAbsent(key, k->new TreeMap<String, FieldValue>())
                        .computeIfAbsent(field, k-> new FieldValue())
                                .versions.put(timestamp, value);


    }

    public String get(long timestamp, String key, String field) {
        Map<String, FieldValue> keyMap = db.get(key);
        if(keyMap==null) {
            return "";
        }
        FieldValue f = keyMap.get(field);
        if(f==null|| f.isExpired(timestamp)) {
            return "";
        }
        TreeMap<Long, String> versions = f.versions;
        Map.Entry<Long,String> entry =  versions.floorEntry(timestamp);
        if(entry==null) {
            return "";
        }
        return entry.getValue();

    }

    public boolean compareAndSet(int timestamp, String key, String field, int expectedValue, int newValue) {
        String cur = get(timestamp, key, field);
        String expected = String.valueOf(expectedValue);
        if(cur.equals(expected)) {
            set(timestamp, key, field, String.valueOf(newValue));
            return true;
        }
        return false;
    }
    public boolean compareAndDelete(long timestamp, String key, String field, int expectedValue) {
        String cur = get(timestamp, key, field);
        String expected = String.valueOf(expectedValue);
        if(cur.equals(expected)) {
            TreeMap<String, FieldValue> keyMap = db.get(key);
            keyMap.get(field).versions.put(timestamp, "\\00");
            return true;
        }
        return false;
    }

    public List<String> scan(long timestamp, String key) {
        TreeMap<String, FieldValue> keyMap = db.get(key);
        List<String> res = new ArrayList<>();

        if(keyMap == null) {
            return res;
        }


        for(Map.Entry<String, FieldValue> entry : keyMap.entrySet()) {
                if(!(entry.getValue().isExpired(timestamp))) {
                    res.add(entry.getKey()+" :: "+entry.getValue().versions.lastEntry().getValue());
                }
        }
        return res;

    }

    public List<String> scanByPrefix(long timestamp, String key, String prefix) {
        TreeMap<String, FieldValue> keyMap = db.get(key);
        List<String> res = new ArrayList<>();

        if(keyMap == null) {
            return res;
        }


        for(Map.Entry<String, FieldValue> entry : keyMap.entrySet()) {
            if(entry.getKey().startsWith(prefix) && !(entry.getValue().isExpired(timestamp))) {
                res.add(entry.getKey()+" :: "+entry.getValue().versions.lastEntry().getValue());
            }
        }
        return res;

    }

    public void setWithTTL(long timestamp, String key, String field, String value, int ttl) {
        db.putIfAbsent(key, new TreeMap<>());
        TreeMap<String, FieldValue> keyMap = db.get(key);
        keyMap.computeIfAbsent(field, f-> new FieldValue())
                .expiry = timestamp+ttl;
        keyMap.get(field).versions.put(timestamp, value);


    }
    public boolean compareAndSetWithTTL(long timestamp, String key, String field, int expectedValue, int newValue, int ttl) {
        String cur = get(timestamp, key, field);
        String expected = String.valueOf(expectedValue);
        if(cur.equals(expected)) {
            setWithTTL(timestamp, key, field, String.valueOf(newValue), ttl);
            return true;
        }
        return false;
    }

}
