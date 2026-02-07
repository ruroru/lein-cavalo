# lein-cavalo

``lein-cavalo`` is a Leiningen plugin that automatically reloads HTML when files change, without requiring an application restart.


# Installation

Add ``lein-cavalo`` to the ``:plugins`` list in your ``project.clj``


```clojure
:plugins [[org.clojars.jj/lein-cavalo "1.0.7"]]
```

# Configuration

add configuration to project.clj
```clojure
:cavalo {
    :server-config {:port 3000              };; Defaults to 8080
    :dirs-to-watch ["foo/bar" "baz"]        ;; Directories to monitor for changes
    :ring-handler example.server/handler    ;; Main Ring handler for serving content
}
```

# Usage

```clojure
lein cavalo
```

## License

Copyright © 2024 [ruroru](https://github.com/ruroru)

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.



[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.jj/lein-cavalo.svg)](https://clojars.org/org.clojars.jj/lein-cavalo)
