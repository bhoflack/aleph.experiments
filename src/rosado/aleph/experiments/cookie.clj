(ns rosado.aleph.experiments.cookie
  (:require [aleph.core :as aleph]
            [aleph.http]
            [ring.middleware.cookies :as cookies]
            [hiccup.page-helpers :as hiccup-ph]
            [clojure.contrib.logging :as log])
  (:use hiccup.core))

(defn page-layout [& content]
  (html
   (hiccup-ph/doctype :xhtml-strict)
   (hiccup-ph/xhtml-tag "en"
     [:head
      [:meta {:http-equiv "Content-Type"
              :content "text/html; charset=utf-8"}]
      [:title "Cookies"]
      [:body content]])))

(defn main-view [status]
  (page-layout
   [:h1 "Hello"]
   status))

(defn login-view []
  (page-layout
   [:h1 "You must be logged in"]
   [:form {:method "get" :action "/login"}
    [:p [:input {:type "text" :name "user"}]]
    [:p [:input {:type "password" :name "password"}]]
    [:p [:input {:type "submit" :value "login"}]]]))

(defn secret-view []
  (page-layout
   [:h1 "This is a secret"]
   [:p "You shoud only see this if you're logged in."]
   [:form {:method "get" :action "/logout"}
    [:input {:type "submit" :value "logout"}]]))

(def simple-response {:status 200
                      :headers {"Content-Type" "text/html"}
                      :body "Greetings from Aleph!"})

(def no-go-response {:status 403
                     :headers {"Content-Type" "text/html"}
                     :body "Forbidden."})

(def users (ref {"john" "doe"}))

(def sessions (ref {}))

(defn user-status [sess-id]
  (if (@sessions sess-id)
    [:p  "You are logged in."]
    [:p "You are logged out." [:a {:href "/login"} "Login"]]))

(defn log-request [msg]
  (fn [r]
    (log/debug (str msg " : " r))
    r))

(declare dispatch-pipeline)
(declare main-view-pipeline)

;;; out pipeline is the final stage, where we only care about :body
;;; and :cookies keys in the incommin map

(def out-pipeline
     (aleph/pipeline
      #(merge simple-response %)
      #(dissoc (cookies/set-cookies %) :cookies)))

(defn get-session-cookie [req]
  (get-in req [:cookies "aleph-session" :value]))

(def main-view-pipeline
     (aleph/pipeline
      (fn [req]
        (let [status (user-status (get-session-cookie req))]
          (log/debug (str "status in main-view: " status " / " (get-session-cookie req)))
          (aleph/redirect out-pipeline {:body (main-view status) :cookies (req :cookies)})))))

(def logout-pipeline
     (aleph/pipeline
      (log-request "entering logout-pipeline")
      (fn [req]
        (when-let [sess-id (get-session-cookie req)]
          (dosync (alter sessions dissoc sess-id)))
        (assoc-in req [:cookies :aleph-session] ""))
      (fn [req] (aleph/redirect main-view-pipeline req))))

(defn parse-query-string
  "Returns a map of the form:
      {:user \"name\" :password \"pass\"}"
  [s]
  (reduce conj {} (map (fn [z]
                         (let [[a b] (.split z "=")]
                           [(keyword a) b]))
                       (.split s "&"))))

(defn login
  "Returns a session id if the login was successful. Otherwise nil."
  [user pass]
  (dosync
   (when-let [password (@users user)]
     (when (= pass password)
       (let [sess-id (str "u" (rand-int 1000) "-" (rand-int 1000))] ;good enough for 1-2 users
         (alter sessions assoc sess-id user)
         sess-id)))))

; login via GET, untill I get around to extracting POST params

(def login-pipeline
     (aleph/pipeline
      (fn [req]
        (if-not (req :query-string)
          (aleph/redirect out-pipeline {:body (login-view)})
          (let [{:keys [user password]} (parse-query-string (req :query-string))]
            (if-let [sess-id (login user password)]
              (aleph/redirect out-pipeline {:body "Logged in." :cookies {:aleph-session sess-id}})
              (aleph/redirect out-pipeline {:body "Bad user or password."})))))))

(def secret-pipeline
     (aleph/pipeline
      (fn [req]
        (aleph/redirect
         out-pipeline
         (if (@sessions (get-session-cookie req))
           (assoc simple-response :body (secret-view))
           no-go-response)))))

(def dispatch-pipeline
     (aleph/pipeline
      cookies/get-cookies
      (log-request "inside dispatch-pipeline")
      #(case (:uri %)
         "/"       (aleph/redirect main-view-pipeline %)
         "/login"  (aleph/redirect login-pipeline %)
         "/logout" (aleph/redirect logout-pipeline %)
         "/secret" (aleph/redirect secret-pipeline %))))

(defn app [channel request]
  (aleph/run-pipeline request
    dispatch-pipeline
    #(aleph/enqueue-and-close channel %)))

(defn run []
  (aleph.http/start-http-server app {:port 8080}))
