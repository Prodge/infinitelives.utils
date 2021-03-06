(ns infinitelives.utils.spatial)

;; WORK IN PROGRESS. NOT FUNCTIONING

;; spatial object hash
;; for quick spatial qurying for things like object location
;; and collisions

(defonce spatial-hashes (atom {}))


;; a spatial hash is keyed by a factor of its position
;; eg. if divider is 10, [x y] = [12 15] and [17 10] are
;; both stored in hash location [1 1]

;; to search a spatial hash you have to search the target
;; cell, and potentially those cells around it

(def default-divider 64)

(defn make-spatial [divider]
  {:hash {}
   :divider divider})

(defn new-spatial! [spatial-keyword & [divider]]
  (swap! spatial-hashes assoc spatial-keyword
         (make-spatial (or divider default-divider))))

(defn hash-position [divider position]
  (vec
   (map
    #(Math/floor (/ % divider))
    position)))

(defn dissoc-in
  "Dissociates an entry from a nested associative structure returning a new
  nested structure. keys is a sequence of keys. Any empty maps that result
  will not be present in the new structure."
  [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (if (seq newmap)
          (assoc m k newmap)
          (dissoc m k)))
      m)
    (dissoc m k)))

(defn add-to-hash-with-hash [{:keys [hash divider] :as spatial} entity-key position key-pos]
  (update-in spatial [:hash key-pos] assoc entity-key position))

(defn add-to-hash [{:keys [hash divider] :as spatial} entity-key position]
  (add-to-hash-with-hash
   spatial entity-key position
   (hash-position divider position)))

(defn remove-from-hash-with-hash [{:keys [hash divider] :as spatial} entity-key position key-pos]
  (update-in spatial [:hash] dissoc-in [key-pos] entity-key))

(defn remove-from-hash [{:keys [hash divider] :as spatial} entity-key position]
  (remove-from-hash-with-hash
   spatial entity-key position
   (hash-position divider position)))

(defn move-in-hash [{:keys [hash divider] :as spatial} entity-key old-pos new-pos]
  (let [old-hash (hash-position divider old-pos)
        new-hash (hash-position divider new-pos)]
    ;; TODO: assert that old key does infact have the entity
    (if (= old-hash new-hash)
      ;; just update the position in the hash
      (assoc-in spatial [:hash old-hash entity-key] new-pos)

      ;; different cells.
      (-> spatial
          (remove-from-hash-with-hash entity-key old-pos old-hash)
          (add-to-hash-with-hash entity-key new-pos new-hash)))))

(defn all-cells [low high]
  (loop [acc '()
         [l & lrest] low
         [h & hrest] high]
    (if (and l h)
      (if (seq acc)
        (recur (for [p acc
                     q (range l (inc h))]
                 (conj p q))
               lrest hrest)
        (recur (for [p (range l (inc h))]
                 (vector p))
               lrest hrest))
      acc)))

(defn query-cells [{:keys [hash divider] :as spatial} start-cell end-cell]
  (apply merge (for [cell (all-cells start-cell end-cell)] (hash cell))))

(defn is-inside? [low high pos]
  (loop [[p & prest] pos
         state true
         [l & lrest] low
         [h & hrest] high]
    (if (and state (seq lrest) (seq hrest))
      (recur prest (and state
                      (<= l p h))
             lrest hrest)
      state)))

(defn query [{:keys [hash divider] :as spatial} start-pos end-pos]
  (let [start (hash-position divider start-pos)
        end (hash-position divider end-pos)]
    ;; now filter for those points actuall in the start-pos end-pos box
    (into {}
          (filter (fn [[k v]] (is-inside? start-pos end-pos v))
                  (query-cells spatial start end)))))

(defn add-to-spatial! [spatial-keyword entity-key position]
  (swap! spatial-hashes update-in [spatial-keyword] add-to-hash entity-key position))

(defn remove-from-spatial [spatial-keyword entity-key position]
  (swap! spatial-hashes update-in [spatial-keyword] remove-from-hash entity-key position))

(defn move-in-spatial [spatial-keyword entity-key old-pos new-pos]
  (swap! spatial-hashes update-in [spatial-keyword] move-in-hash entity-key old-pos new-pos))
