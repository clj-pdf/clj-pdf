(ns clj-pdf.test.utils
  (:require [clojure.test :refer :all]
            [clj-pdf.utils :refer :all]))

(deftest test-flatten-seqs
  (is (= [1 2 3 4 5 6 7 8 9]
         (flatten-seqs (list 1
                             (list (list 2 3)
                                   4
                                   (list 5 6))
                             (list)
                             (list (list (list 7))
                                   (list (list 8)
                                         (list 9))))))))
