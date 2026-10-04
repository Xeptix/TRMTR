package com.trmtgtnh.client.render;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.trmtgtnh.SourceTree;

/**
 * No posture may be written above the line that wipes it.
 *
 * <p>
 * The golem's pose is one long method that lays each posture over the last: the walk sets an angle,
 * the carry lifts it, the work stroke swings it, the mouthful raises it, the run hunches it. Almost
 * every line composes - {@code +=} onto whatever is there already - and the few that do not are
 * there for a reason. The run assigns the arm roll outright rather than adding to it, because a
 * rolled arm passes through a leg and the run must be sure of that axis whatever else has happened.
 *
 * <p>
 * Which makes an outright assignment a quiet eraser of everything written to that field above it,
 * and that is not a theory. The eating pose rolled both arms inward so the hands could reach a head
 * that twenty-seven units of elbowless arm cannot otherwise reach - and it did so twenty lines above
 * the run's {@code rotateAngleZ = 0F}. It compiled, it was reviewed, and from 0.9.182 until the
 * version that added this test not one frame of it was ever drawn: the arms came up to the
 * horizontal and stopped, hands a shoulder's width either side of the head they were reaching for.
 * Nothing failed. The golem simply ate with its arms out.
 *
 * <p>
 * So the rule is positional rather than about any one posture: for each part and each axis, no
 * composing write may sit above the last outright assignment to it. Everything a pose contributes
 * either composes onto what the assignment leaves, or is folded into the assignment itself - which
 * is what the mouthful's roll now does. A new posture that wants an axis somebody else assigns will
 * fail here rather than three months later in somebody's screenshot.
 */
class ModelPoseOrderTest {

    private static final String MODEL = "com/trmtgtnh/client/render/ModelGolemOfWays.java";

    /** {@code part.rotateAngleX op} - the part, the axis, and whether it composes or assigns. */
    private static final Pattern WRITE = Pattern
        .compile("\\b([a-zA-Z]+)\\.(rotateAngle[XYZ])\\s*(\\+=|-=|\\*=|/=|=)(?!=)");

    @Test
    void no_pose_is_written_above_the_line_that_assigns_it() throws IOException {
        List<String> lines = SourceTree.lines(MODEL);

        // The line of the last outright assignment to each part and axis, and every composing
        // write to it, in the order they are read.
        Map<String, Integer> assignedAt = new LinkedHashMap<String, Integer>();
        Map<String, List<Integer>> composedAt = new LinkedHashMap<String, List<Integer>>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i)
                .trim();
            // Prose in this file names these fields when it explains them, and a comment that
            // mentions a write is not a write.
            if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) continue;
            Matcher found = WRITE.matcher(line);
            while (found.find()) {
                String field = found.group(1) + "." + found.group(2);
                if ("=".equals(found.group(3))) {
                    assignedAt.put(field, Integer.valueOf(i + 1));
                } else {
                    List<Integer> at = composedAt.get(field);
                    if (at == null) {
                        at = new ArrayList<Integer>();
                        composedAt.put(field, at);
                    }
                    at.add(Integer.valueOf(i + 1));
                }
            }
        }

        if (assignedAt.isEmpty()) {
            fail(
                MODEL + " has no angle assignments in it at all, which means this test is reading "
                    + "the wrong file or the pose has moved. Point it at the pose.");
        }

        List<String> lost = new ArrayList<String>();
        for (Map.Entry<String, List<Integer>> entry : composedAt.entrySet()) {
            Integer assigned = assignedAt.get(entry.getKey());
            if (assigned == null) continue;
            for (Integer at : entry.getValue()) {
                if (at.intValue() < assigned.intValue()) {
                    lost.add(
                        entry.getKey() + " is added to at line "
                            + at
                            + " and then assigned outright at line "
                            + assigned
                            + ", so line "
                            + at
                            + " is never drawn");
                }
            }
        }

        if (!lost.isEmpty()) {
            StringBuilder message = new StringBuilder(
                "A posture is being written above the line that wipes it. These contributions "
                    + "compile and are thrown away before anything is drawn:\n");
            for (String one : lost) {
                message.append("  ")
                    .append(one)
                    .append('\n');
            }
            message.append(
                "Either move the contribution below the assignment, or fold it into the "
                    + "assignment as the mouthful's arm roll is folded into the run's.");
            fail(message.toString());
        }
    }
}
