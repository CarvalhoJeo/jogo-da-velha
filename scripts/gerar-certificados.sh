#!/usr/bin/env bash
# Gera o certificado autoassinado do servidor e o truststore do cliente com o keytool.
#
#   scripts/gerar-certificados.sh                  # localhost e 127.0.0.1
#   scripts/gerar-certificados.sh 192.168.0.10     # acrescenta o IP do servidor na rede
#
# Senhas: VELHA_KEYSTORE_PASSWORD e VELHA_TRUSTSTORE_PASSWORD (padrão "changeit").
# Os arquivos ficam em certs/, que está no .gitignore e nunca vai para o Git.
set -euo pipefail

cd "$(dirname "$0")/.."
OUT=certs
KS_PASS="${VELHA_KEYSTORE_PASSWORD:-changeit}"
TS_PASS="${VELHA_TRUSTSTORE_PASSWORD:-changeit}"

SAN="dns:localhost,ip:127.0.0.1"
for host in "$@"; do
  if [[ "$host" =~ ^[0-9.]+$ ]]; then SAN="$SAN,ip:$host"; else SAN="$SAN,dns:$host"; fi
done

mkdir -p "$OUT"
rm -f "$OUT/server-keystore.p12" "$OUT/client-truststore.p12" "$OUT/server.cer"

keytool -genkeypair -alias velha-server -keyalg EC -groupname secp256r1 -validity 365 \
  -dname "CN=Jogo da Velha, OU=Redes, O=Unisinos, C=BR" -ext "SAN=$SAN" \
  -storetype PKCS12 -keystore "$OUT/server-keystore.p12" -storepass "$KS_PASS" -keypass "$KS_PASS"

keytool -exportcert -alias velha-server -keystore "$OUT/server-keystore.p12" \
  -storepass "$KS_PASS" -file "$OUT/server.cer"

keytool -importcert -noprompt -alias velha-server -file "$OUT/server.cer" \
  -storetype PKCS12 -keystore "$OUT/client-truststore.p12" -storepass "$TS_PASS"

echo
echo "Pronto:"
echo "  $OUT/server-keystore.p12    -> fica só na máquina do servidor"
echo "  $OUT/client-truststore.p12  -> copie para cada máquina cliente (mesmo caminho)"
echo "  SAN: $SAN"
