Example programs for the code generator of phase 3a and 3b.
Each NAME.txt has a NAME.expected with the output the generated C++ must print.
The calls_*.txt programs call functions and are translated by inlining (phase 3b).
calls_labels inlines one function with a loop several times. Each copy needs labels of its own.
calls_nested has a function inside a function and calls inside conditions and arguments.
calls_void_float calls void procedures in a program with floating point numbers.
CodeGeneratorTest compiles every program with g++ and compares the output. Without g++ these checks are skipped.
