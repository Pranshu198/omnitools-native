package com.jackbhai.omnitools.nativ.tools;

/** One row of the File Manager-lite listing. Plain data, no Android
 *  types, so sorting/formatting over it is JVM-testable. {@code uri}
 *  holds a content:// (or file) URI string; {@code docId} is the SAF
 *  document id when the row came from a document tree. */
public final class FileItem {
    public String name;
    public long size;        // bytes; -1 when unknown (some SAF providers)
    public long modifiedMs;  // epoch millis; 0 when unknown
    public boolean isDir;
    public String uri;
    public String docId;     // SAF only, else null
    public String mime;      // may be null

    public FileItem(String name, long size, long modifiedMs, boolean isDir,
                    String uri, String docId, String mime) {
        this.name = name; this.size = size; this.modifiedMs = modifiedMs;
        this.isDir = isDir; this.uri = uri; this.docId = docId; this.mime = mime;
    }
}
