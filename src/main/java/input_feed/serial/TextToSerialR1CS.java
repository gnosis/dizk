package input_feed.serial;

import algebra.fields.AbstractFieldElementExpanded;
import relations.objects.*;
import relations.r1cs.R1CSRelation;
import scala.Tuple2;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class TextToSerialR1CS<FieldT extends AbstractFieldElementExpanded<FieldT>> {
    private final String filePath;
    private final int numPrimary;
    private final int numAuxiliary;
    private final int numConstraints;
    private final FieldT fieldParameters;
    private boolean negateCMatrix;

    public TextToSerialR1CS(
            final String _filePath,
            final FieldT _fieldParameters,
            final boolean _negateCMatrix) {
        filePath = _filePath;
        fieldParameters = _fieldParameters;
        negateCMatrix = _negateCMatrix;

        String[] parameters = new String[3];
        try {
            parameters = new BufferedReader(
                    new FileReader(filePath + ".size")).readLine().split(" ");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
        numPrimary = Integer.parseInt(parameters[0]);
        numAuxiliary = Integer.parseInt(parameters[1]);
        numConstraints = Integer.parseInt(parameters[2]);
    }

    public TextToSerialR1CS(final String _filePath, final FieldT _fieldParameters) {
        this(_filePath, _fieldParameters, false);
    }

    public R1CSRelation<FieldT> loadR1CS() {
        final R1CSConstraints<FieldT> constraints = new R1CSConstraints<>();

        try {

            BufferedReader matrixA = new BufferedReader(new FileReader(filePath + ".a"));
            BufferedReader matrixB = new BufferedReader(new FileReader(filePath + ".b"));
            BufferedReader matrixC = new BufferedReader(new FileReader(filePath + ".c"));

            for (int currRow = 0; currRow < numConstraints; currRow++) {
                LinearCombination<FieldT> A = makeRowAt(currRow, matrixA, false);
                LinearCombination<FieldT> B = makeRowAt(currRow, matrixB, false);
                LinearCombination<FieldT> C = makeRowAt(currRow, matrixC, negateCMatrix);

                constraints.add(new R1CSConstraint<>(A, B, C));
            }
            matrixA.close();
            matrixB.close();
            matrixC.close();

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }

        return new R1CSRelation<>(constraints, numPrimary, numAuxiliary);
    }

    public Tuple2<Assignment<FieldT>, Assignment<FieldT>> loadWitness() {

        Assignment<FieldT> primary = new Assignment<>();
        Assignment<FieldT> auxiliary = new Assignment<>();

        try {
            // Prepend the value 1 to primary input
            primary.add(fieldParameters.one());

            BufferedReader publicFile = new BufferedReader(new FileReader(filePath + ".public"));
            loadAssignment(primary, publicFile, numPrimary);

            BufferedReader auxFile = new BufferedReader(new FileReader(filePath + ".aux"));
            loadAssignment(auxiliary, auxFile, numAuxiliary);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
        return new Tuple2<>(primary, auxiliary);
    }

    private void loadAssignment(Assignment<FieldT> assignment, BufferedReader file, int expectedSize) throws IOException {
        String nextLine;
        while ((nextLine = file.readLine()) != null) {
            final FieldT value = fieldParameters.construct(nextLine);
            assignment.add(value);
        }
        file.close();
        assert (expectedSize == assignment.size());
    }

    private LinearCombination<FieldT> makeRowAt(long index, BufferedReader reader, boolean negate) {
        final LinearCombination<FieldT> combination = new LinearCombination<>();
        try {
            final int readAheadLimit = 100;
            String nextLine;
            reader.mark(readAheadLimit);
            while ((nextLine = reader.readLine()) != null) {
                String[] tokens = nextLine.split(" ");

                int col = Integer.parseInt(tokens[0]);
                int row = Integer.parseInt(tokens[1]);

                if (index == row) {
                    reader.mark(readAheadLimit);
                    FieldT value = fieldParameters.construct(tokens[2]);
                    if (negate) {
                        value = value.negate();
                    }
                    combination.add(new LinearTerm<>(col, value));
                } else if (row < index) {
                    System.out.format(
                            "[WARNING] Term with index %d after index %d will be ignored.\n", row, index);
                } else {
                    reader.reset();
                    return combination;
                }
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
        return combination;
    }
}
