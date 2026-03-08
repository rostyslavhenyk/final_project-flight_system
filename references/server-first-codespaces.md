# Server‑First in GitHub Codespaces (Ktor + HTMX)

This guide shows how to run the **server‑first** pattern in **GitHub Codespaces** with **Ktor** and **HTMX**. It includes a minimal devcontainer, project skeleton, and common fixes.

---

## What you’ll get
- One‑click Codespaces environment (JDK 21 + Gradle).
- Ktor server bound to `0.0.0.0`, using `$PORT` or `8080`.
- FreeMarker templating (official Ktor support) with HTMX for progressive enhancement.
- Works with JavaScript **off** (no‑JS parity).

---

## 1) `.devcontainer/devcontainer.json`
```json
{
  "name": "COMP2850 Ktor (server-first)",
  "build": { "dockerfile": "Dockerfile" },
  "forwardPorts": [8080],
  "portsAttributes": {
    "8080": { "label": "Ktor app", "onAutoForward": "openPreview" }
  },
  "postCreateCommand": "chmod +x ./gradlew || true",
  "customizations": {
    "vscode": {
      "extensions": [
        "vscjava.vscode-java-pack",
        "fwcd.kotlin",
        "redhat.vscode-yaml",
        "editorconfig.editorconfig"
      ]
    }
  }
}
```

## 2) `.devcontainer/Dockerfile`
```dockerfile
FROM mcr.microsoft.com/devcontainers/java:1-21-bullseye

# Optional: Gradle cache to speed up builds
ENV GRADLE_USER_HOME=/workspace/.gradle
```

## 3) `build.gradle.kts` (essentials)
Uses **Ktor 2.x + Netty + FreeMarker** for smooth Codespaces support.
```kotlin
plugins {
    application
    kotlin("jvm") version "2.0.0"
    id("io.ktor.plugin") version "2.3.11"
}

repositories { mavenCentral() }

val ktorVersion = "2.3.11"

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-freemarker-jvm:$ktorVersion")
    implementation("ch.qos.logback:logback-classic:1.4.14")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
}

application {
    mainClass.set("MainKt")
}

tasks.withType<JavaExec> {
    systemProperty("io.ktor.development", "true")
}
```

## 4) `src/main/kotlin/Main.kt`
```kotlin
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.freemarker.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.http.*
import freemarker.cache.ClassTemplateLoader

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

fun Application.module() {
    install(FreeMarker) {
        templateLoader = ClassTemplateLoader(this::class.java.classLoader, "templates")
    }
    install(ContentNegotiation)

    routing {
        get("/") {
            // Full-page render (server-first)
            call.respond(FreeMarkerContent("tasks.ftl", mapOf("tasks" to listOf("Example task"))))
        }

        post("/tasks") {
            val params = call.receiveParameters()
            val title = params["title"]?.trim().orEmpty()
            if (title.isBlank()) {
                // 422 for HTMX validation errors; full page flow would re-render with errors
                call.respond(HttpStatusCode.UnprocessableEntity, "Title is required")
                return@post
            }
            // PRG for full-page; for HTMX you can return a fragment or tell HTMX to redirect
            call.response.headers.append("HX-Redirect", "/")
            call.respond(HttpStatusCode.OK)
        }
    }
}
```

## 5) `src/main/re./templates/tasks.ftl` (full page + HTMX)
```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>Tasks</title>
  <script src="https://unpkg.com/htmx.org@1.9.10"></script>
</head>
<body>
  <main>
    <h1>Tasks</h1>

    <section aria-live="polite" id="alerts"></section>

    <form action="/tasks" method="post"
          hx-post="/tasks"
          hx-target="#alerts"
          hx-swap="innerHTML">
      <label for="title">Title</label>
      <input id="title" name="title" required>
      <button type="submit">Add</button>
    </form>

    <ul>
      <#list tasks as t>
        <li>${t}</li>
      </#list>
    </ul>
  </main>
</body>
</html>
```

---

## 6) How to run in Codespaces
1. Open the repo in Codespaces (with the `.devcontainer` files present).  
2. In the terminal, run:
   ```bash
   ./gradlew run
   ```
3. Codespaces will auto‑forward **port 8080** and open a preview. If not, open the **Ports** tab and make port 8080 **Public** or click the forwarded URL.

---

## 7) Common gotchas (fix quickly)
- **Bind host** to `0.0.0.0` (not `localhost`), or Codespaces can’t reach it.  
- **Use `$PORT`** if Codespaces provides one; default to 8080 otherwise.  
- **Official templates**: stick to FreeMarker/Thymeleaf/Mustache/Velocity to reduce surprises.  
- **Fragments vs pages**: HTMX sends `HX-Request: true`; branch accordingly if you add fragment routes.  
- **Live reload**: `-Dio.ktor.development=true` is enabled via Gradle task config.

---

## 8) Optional enhancements
- **`hx-boost="true"`** to progressively enhance links/forms without changing routes.  
- **OOB updates** (`hx-swap-oob`) for global banners like flash messages.  
- **Accessibility checks**: keyboard‑only test, visible focus, labelled inputs, `aria-live` for status.  
- **cURL HTMX**: 
  ```bash
  curl -H "HX-Request: true" http://localhost:8080/
  ```

---

## 9) Suggested repo structure
```
.
├─ .devcontainer/
│  ├─ Dockerfile
│  └─ devcontainer.json
├─ src/
│  ├─ main/
│  │  ├─ kotlin/
│  │  │  └─ Main.kt
│  │  └─ re./
│  │     └─ templates/
│  │        └─ tasks.ftl
├─ build.gradle.kts
└─ settings.gradle.kts
```

---

