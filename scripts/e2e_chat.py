#!/usr/bin/env python3
import argparse
import base64
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

DEFAULT_MESSAGES = [
    "Tengo fiebre y dolor de garganta desde ayer.",
    "La fiebre llego a 38.5 y el dolor es 5 de 10.",
    "No tengo dificultad para respirar ni dolor de pecho.",
    "No tengo enfermedades previas, no tomo medicacion y no tengo alergias.",
]

ACTIVE_CONSULTA_STATES = (
    "PENDIENTE",
    "HOSPITAL_SELECCIONADO",
    "PRETRIAGE_FINALIZADO",
    "PRETRIAGE_EN_PROCESO",
    "EN_COLA",
    "LLAMADO",
    "EN_ESPERA",
    "ATRASADO",
    "EN_ATENCION",
)

ACTIVE_ENTRADA_STATES = (
    "EN_COLA",
    "LLAMADO",
    "EN_ESPERA",
    "ATRASADO",
    "EN_ATENCION",
)


def load_dotenv(path: Path) -> dict:
    values = {}
    if not path.exists():
        return values
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip().strip('"').strip("'")
    return values


def env_value(dotenv: dict, key: str, default=None):
    return os.environ.get(key) or dotenv.get(key) or default


def load_messages_file(path: Path) -> list[str]:
    if not path.exists():
        raise RuntimeError(f"Messages file does not exist: {path}")
    messages = []
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        messages.append(line)
    if not messages:
        raise RuntimeError(f"Messages file has no messages: {path}")
    return messages


def http_json(method, url, body=None, token=None, timeout=120):
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"

    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            payload = response.read().decode("utf-8")
            if not payload:
                return None
            return json.loads(payload)
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {method} {url} failed: {exc.code} {detail}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"Cannot connect to {url}: {exc.reason}") from exc


def extract_access_token(login_response):
    token_value = login_response.get("token") if isinstance(login_response, dict) else None
    if not token_value:
        raise RuntimeError(f"Login response does not contain token: {login_response}")

    if isinstance(token_value, str) and token_value.count(".") == 2:
        return token_value

    if isinstance(token_value, str):
        try:
            nested = json.loads(token_value)
            if nested.get("access_token"):
                return nested["access_token"]
            if nested.get("id_token"):
                return nested["id_token"]
        except json.JSONDecodeError:
            pass

    raise RuntimeError("Could not extract access_token from /api/login response")


def decode_jwt_subject(token: str) -> str:
    try:
        payload = token.split(".")[1]
        payload += "=" * (-len(payload) % 4)
        decoded = base64.urlsafe_b64decode(payload.encode("utf-8"))
        claims = json.loads(decoded.decode("utf-8"))
        subject = claims.get("sub")
        if not subject:
            raise RuntimeError("JWT does not contain sub claim")
        return subject
    except Exception as exc:
        raise RuntimeError("Could not decode JWT subject") from exc


def sql_quote(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def mask_email(value: str) -> str:
    if not value or "@" not in value:
        return "***"
    name, domain = value.split("@", 1)
    visible = name[:2] if len(name) > 2 else name[:1]
    return f"{visible}***@{domain}"


def run_psql(sql: str, db_user: str, db_name: str, container: str):
    command = [
        "docker",
        "exec",
        "-i",
        container,
        "psql",
        "-U",
        db_user,
        "-d",
        db_name,
        "-v",
        "ON_ERROR_STOP=1",
    ]
    completed = subprocess.run(command, input=sql, text=True, capture_output=True)
    if completed.returncode != 0:
        raise RuntimeError(
            "psql seed failed\n"
            f"STDOUT:\n{completed.stdout}\n"
            f"STDERR:\n{completed.stderr}"
        )
    return completed.stdout


def seed_database(args, subject: str, email: str):
    active_consultas = ", ".join(sql_quote(s) for s in ACTIVE_CONSULTA_STATES)
    active_entradas = ", ".join(sql_quote(s) for s in ACTIVE_ENTRADA_STATES)
    sql = f"""
BEGIN;

INSERT INTO usuario_auth (id, nombre, apellido, numero_documento, tipo_documento, correo_electronico)
VALUES ({sql_quote(subject)}, 'Paciente', 'E2E', '99999999', 'DNI', {sql_quote(email)})
ON CONFLICT (id) DO UPDATE SET correo_electronico = EXCLUDED.correo_electronico;

INSERT INTO paciente (auth_id)
SELECT {sql_quote(subject)}
WHERE NOT EXISTS (SELECT 1 FROM paciente WHERE auth_id = {sql_quote(subject)});

UPDATE entrada_cola ec
SET estado = 'CANCELADA'
FROM consulta_medica cm, paciente p
WHERE ec.id_consulta_medica = cm.id
  AND cm.id_paciente = p.id
  AND p.auth_id = {sql_quote(subject)}
  AND ec.estado IN ({active_entradas});

UPDATE consulta_medica cm
SET estado_consulta = 'CANCELADA'
FROM paciente p
WHERE cm.id_paciente = p.id
  AND p.auth_id = {sql_quote(subject)}
  AND cm.estado_consulta IN ({active_consultas});

DELETE FROM mensaje m
USING chat c, paciente p
WHERE m.chat_id = c.id
  AND c.paciente_id = p.id
  AND p.auth_id = {sql_quote(subject)};

DELETE FROM chat c
USING paciente p
WHERE c.paciente_id = p.id
  AND p.auth_id = {sql_quote(subject)};

INSERT INTO especialidad_medica (codigo, nombre)
VALUES ({sql_quote(args.specialty)}, {sql_quote(args.specialty_name)})
ON CONFLICT (codigo) DO UPDATE SET nombre = EXCLUDED.nombre;

INSERT INTO hospital (place_id, nombre)
SELECT {sql_quote(args.place_id)}, {sql_quote(args.hospital_name)}
WHERE NOT EXISTS (SELECT 1 FROM hospital WHERE place_id = {sql_quote(args.place_id)});

INSERT INTO hospital_especialidad_medica (id_hospital, id_especialidad_medica)
SELECT h.id, e.id
FROM hospital h, especialidad_medica e
WHERE h.place_id = {sql_quote(args.place_id)}
  AND e.codigo = {sql_quote(args.specialty)}
  AND NOT EXISTS (
      SELECT 1
      FROM hospital_especialidad_medica he
      WHERE he.id_hospital = h.id
        AND he.id_especialidad_medica = e.id
  );

COMMIT;
"""
    run_psql(sql, args.db_user, args.db_name, args.db_container)


def check_ollama(model: str, timeout=5):
    try:
        response = http_json("GET", "http://localhost:11434/api/tags", timeout=timeout)
    except RuntimeError as exc:
        raise RuntimeError("Ollama is not reachable at http://localhost:11434") from exc
    models = [item.get("name") for item in response.get("models", [])]
    if model not in models:
        raise RuntimeError(f"Ollama model {model!r} is not installed. Available models: {models}")


def main():
    parser = argparse.ArgumentParser(description="Run a real E2E chat flow against the backend and Ollama.")
    parser.add_argument("--backend-url", default=os.environ.get("BACKEND_URL", "http://localhost:8080"))
    parser.add_argument("--env-file", default=".env")
    parser.add_argument("--db-user", default=os.environ.get("DB_USER", "myuser"))
    parser.add_argument("--db-name", default=os.environ.get("DB_NAME", "pretriage_db"))
    parser.add_argument("--db-container", default=os.environ.get("DB_CONTAINER", "postgre"))
    parser.add_argument("--place-id", default=os.environ.get("E2E_HOSPITAL_PLACE_ID", "e2e-hospital-chat"))
    parser.add_argument("--hospital-name", default=os.environ.get("E2E_HOSPITAL_NAME", "Hospital E2E Chat"))
    parser.add_argument("--specialty", default=os.environ.get("E2E_ESPECIALIDAD", "CLINICA_MEDICA"))
    parser.add_argument("--specialty-name", default=os.environ.get("E2E_ESPECIALIDAD_NOMBRE", "Clinica medica"))
    parser.add_argument("--ollama-model", default=os.environ.get("E2E_OLLAMA_MODEL", "llama3.2:3b"))
    parser.add_argument("--skip-ollama-check", action="store_true")
    parser.add_argument("--message", action="append", dest="messages", help="Message to send. Repeat to override defaults.")
    parser.add_argument("--messages-file", default=os.environ.get("E2E_MESSAGES_FILE"), help="Text file with one patient message per non-empty line.")
    parser.add_argument("--debug-log", default=os.environ.get("E2E_DEBUG_LOG", "scripts/e2e_chat_last_debug.json"))
    args = parser.parse_args()

    dotenv = load_dotenv(Path(args.env_file))
    email = env_value(dotenv, "AUTH0_TEST_USERNAME")
    password = env_value(dotenv, "AUTH0_TEST_PASSWORD")
    if not email or not password:
        raise RuntimeError("AUTH0_TEST_USERNAME and AUTH0_TEST_PASSWORD must exist in .env or environment")

    if args.messages:
        messages = args.messages
    elif args.messages_file:
        messages = load_messages_file(Path(args.messages_file))
    else:
        messages = DEFAULT_MESSAGES
    backend_url = args.backend_url.rstrip("/")

    if not args.skip_ollama_check:
        check_ollama(args.ollama_model)
        print(f"Ollama OK: {args.ollama_model}")

    login = http_json("POST", f"{backend_url}/api/login", {"email": email, "password": password})
    token = extract_access_token(login)
    subject = decode_jwt_subject(token)
    print("Login OK")

    seed_database(args, subject, email)
    print("Seed DB OK")

    http_json(
        "POST",
        f"{backend_url}/api/atencion/hospital",
        {"placeId": args.place_id, "codigoEspecialidad": args.specialty},
        token=token,
    )
    print("Hospital seleccionado OK")

    chat = http_json("POST", f"{backend_url}/api/chat", token=token)
    chat_id = chat["id"]
    print(f"Chat creado OK: id={chat_id}")

    debug_turns = []
    final_turn = None
    for index, message in enumerate(messages, start=1):
        turn = http_json(
            "POST",
            f"{backend_url}/api/chat/{chat_id}/mensajes",
            {"contenido": message},
            token=token,
            timeout=180,
        )
        bot_text = (turn.get("respuesta") or {}).get("contenido")
        debug_turns.append({"turno": index, "paciente": message, "bot": bot_text, "atencionEstimada": turn.get("atencionEstimada")})
        print(f"Turno {index} OK | bot={bot_text!r}")
        if turn.get("atencionEstimada") is not None:
            final_turn = turn
            print(f"Triage finalizado OK | atencionEstimada={turn['atencionEstimada']}")
            break
        time.sleep(0.2)

    current_chat = http_json("GET", f"{backend_url}/api/chat/{chat_id}", token=token)
    if final_turn is None and current_chat.get("finalizado"):
        print("Chat finalizado OK")
    elif final_turn is None:
        raise RuntimeError("The scripted conversation ended but triage was not finalized")

    validation_sql = f"""
SELECT cm.id AS consulta_id, cm.estado_consulta, ec.estado AS entrada_estado
FROM consulta_medica cm
JOIN paciente p ON p.id = cm.id_paciente
LEFT JOIN entrada_cola ec ON ec.id_consulta_medica = cm.id
WHERE p.auth_id = {sql_quote(subject)}
ORDER BY cm.id DESC
LIMIT 1;
"""
    output = run_psql(validation_sql, args.db_user, args.db_name, args.db_container)
    print("DB validation:")
    print(output.strip())

    if "EN_COLA" not in output:
        raise RuntimeError("Expected latest consulta/entrada to be EN_COLA")

    resultado_sql = f"""
SELECT resultado_triage_json
FROM chat
WHERE id = {chat_id};
"""
    resultado_output = run_psql(resultado_sql, args.db_user, args.db_name, args.db_container)

    prioridad_sql = f"""
SELECT cm.id AS consulta_id,
       cm.estado_consulta,
       cm.nivel_de_gravedad_bot,
       ec.prioridad,
       ec.estado AS entrada_estado,
       ec.orden_relativo
FROM consulta_medica cm
JOIN paciente p ON p.id = cm.id_paciente
LEFT JOIN entrada_cola ec ON ec.id_consulta_medica = cm.id
WHERE p.auth_id = {sql_quote(subject)}
ORDER BY cm.id DESC
LIMIT 1;
"""
    prioridad_output = run_psql(prioridad_sql, args.db_user, args.db_name, args.db_container)

    mensajes_sql = f"""
SELECT m.fecha_hora_envio, m.autor, m.contenido
FROM mensaje m
WHERE m.chat_id = {chat_id}
ORDER BY m.fecha_hora_envio, m.id;
"""
    mensajes_output = run_psql(mensajes_sql, args.db_user, args.db_name, args.db_container)

    debug_payload = {
        "chatId": chat_id,
        "usuario": mask_email(email),
        "hospitalPlaceId": args.place_id,
        "especialidad": args.specialty,
        "turnosScript": debug_turns,
        "resultadoTriageSql": resultado_output.strip(),
        "prioridadSql": prioridad_output.strip(),
        "mensajesSql": mensajes_output.strip(),
    }
    debug_path = Path(args.debug_log)
    debug_path.parent.mkdir(parents=True, exist_ok=True)
    debug_path.write_text(json.dumps(debug_payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Debug log escrito en {debug_path}")

    print("Prioridad asignada:")
    print(prioridad_output.strip())
    print("E2E chat OK")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        sys.exit(1)







