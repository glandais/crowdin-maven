package com.googlecode.crowdin.maven.tool;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.apache.maven.artifact.Artifact;

@RequiredArgsConstructor
public class SpecialArtifact implements Artifact {

    @Delegate
    final Artifact delegator;

    public int hashCode() {
        int result = 17;
        result = 37 * result + getGroupId().hashCode();
        result = 37 * result + getArtifactId().hashCode();
        return result;
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Artifact)) {
            return false;
        }
        Artifact a = (Artifact) o;
        // We don't consider the version range in the comparison, just the
        // resolved version
        if (!a.getGroupId().equals(getGroupId())) {
            return false;
        } else {
            return a.getArtifactId().equals(getArtifactId());
        }
    }

    @Override
    public String toString() {
        return getGroupId() + ":" + getArtifactId();
    }

}
