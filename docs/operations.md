# 運用手順

## 日常確認

1. `/actuator/health` が `UP` であることを監視します。
2. 管理画面の「最終実行」でFAILED、除外件数、処理時間を確認します。
3. FAILEDの場合は実行詳細のエラーコードと試行履歴を確認します。
4. Google Sheets側の共有権限、API利用状況、入力ヘッダーを確認してから再開します。

## バックアップと復元

本番変更前と定期運用ではPostgreSQLを論理バックアップします。

```bash
docker compose exec -T postgres pg_dump -U sales_app -Fc sales_aggregation > sales_aggregation.dump
```

復元は空の検証DBで先にリハーサルし、アプリを停止してから実施します。

```bash
docker compose stop app
docker compose exec -T postgres pg_restore -U sales_app -d sales_aggregation --clean --if-exists < sales_aggregation.dump
docker compose --profile app up -d app
```

復元後はFlywayのバージョン、Profile件数、直近履歴、Quartzの次回実行を確認します。

## 障害時の確認順

| エラーコード | 最初に確認する項目 |
|---|---|
| `GOOGLE_SHEETS_UNAVAILABLE` | API稼働、ネットワーク、共有権限、サービスアカウント |
| `INVALID_SHEET_HEADER` | 入力シート1行目と列マッピング |
| `ROW_LIMIT_EXCEEDED` | 入力件数と `MAX_SALES_ROWS` |
| `DATABASE_ERROR` | PostgreSQL、空き容量、接続数 |
| `CONCURRENT_EXECUTION` | 同じProfileの先行実行が完了しているか |

Google API障害は接続・読込タイムアウトでFAILEDになります。原因解消後は同じ実行IDを再開し、試行履歴で復旧経緯を確認します。

## セキュリティ

- 公開環境はTLS終端の背後に置き、`SESSION_COOKIE_SECURE=true` にします。
- `.env` とサービスアカウントJSONはSecret Manager等で配布し、Gitへ保存しません。
- 管理パスワードとサービスアカウント鍵を定期的にローテーションします。
- `/actuator` はhealth以外を外部公開しません。

## 保持期限

完了済み実行は `EXECUTION_HISTORY_DAYS` 後に毎日削除されます。実行履歴、試行履歴、入力エラー、残存する途中データは外部キーにより同時に削除されます。QUEUEDとRUNNINGは自動削除しません。
