# 本地签名配置

公开仓库不会提交真实 keystore 和密码。需要固定调试签名时，可以按下面步骤在本机创建：

```bash
mkdir -p keystore signing
keytool -genkeypair \
  -v \
  -keystore keystore/android-forward-debug.jks \
  -storepass change-me \
  -keypass change-me \
  -alias androidforward \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -dname "CN=Android Forward Debug,O=Personal,C=CN"
cp signing/debug-signing.properties.example signing/debug-signing.properties
```

然后把 `signing/debug-signing.properties` 里的密码改成真实值。该文件和 keystore 都已加入 `.gitignore`。

Google Play 上架需要 release AAB 使用本地 upload key 签名。建议把 upload key 放在 `keystore/android-forward-upload.jks`，并用未提交的 `signing/release-signing.properties` 保存路径和密码。该文件与 keystore 一样只保留在本机，不要提交到 Git，也要单独备份。
