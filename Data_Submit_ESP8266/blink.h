void blinkSetup() {
  pinMode(LED_BUILTIN, OUTPUT);
  // digitalWrite(LED_BUILTIN, LOW);
  digitalWrite(LED_BUILTIN, HIGH);
}

void blink(int16_t ledON){
  digitalWrite(LED_BUILTIN, LOW);
  delay(ledON);
  digitalWrite(LED_BUILTIN, HIGH);
}