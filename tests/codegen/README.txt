Example programs for the code generator of phase 3a and 3b.
Each NAME.txt has a NAME.expected with the output the generated C++ must print.
The calls_*.txt programs call functions and are translated by inlining (phase 3b): several inlinings of one function
with loops inside (labels), a function inside a function, calls inside conditions and arguments, and void procedures with floats.
CodeGeneratorTest compiles every program with g++ and compares the output. Without g++ these checks are skipped.
