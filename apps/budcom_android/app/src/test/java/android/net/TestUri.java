package android.net;

import android.os.Parcel;
import java.util.Collections;
import java.util.List;

public final class TestUri extends Uri {
    private TestUri() {}

    public static Uri create() { return new TestUri(); }
    @Override public boolean isHierarchical() { return true; }
    @Override public boolean isRelative() { return false; }
    @Override public String getScheme() { return "content"; }
    @Override public String getSchemeSpecificPart() { return "//test/document"; }
    @Override public String getEncodedSchemeSpecificPart() { return "//test/document"; }
    @Override public String getAuthority() { return "test"; }
    @Override public String getEncodedAuthority() { return "test"; }
    @Override public String getUserInfo() { return null; }
    @Override public String getEncodedUserInfo() { return null; }
    @Override public String getHost() { return "test"; }
    @Override public int getPort() { return -1; }
    @Override public String getPath() { return "/document"; }
    @Override public String getEncodedPath() { return "/document"; }
    @Override public String getQuery() { return null; }
    @Override public String getEncodedQuery() { return null; }
    @Override public String getFragment() { return null; }
    @Override public String getEncodedFragment() { return null; }
    @Override public List<String> getPathSegments() { return Collections.singletonList("document"); }
    @Override public String getLastPathSegment() { return "document"; }
    @Override public Builder buildUpon() { throw new UnsupportedOperationException(); }
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel parcel, int flags) {}
    @Override public String toString() { return "content://test/document"; }
}
