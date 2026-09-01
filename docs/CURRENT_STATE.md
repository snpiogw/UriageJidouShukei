# 現在の状態

このファイルは現在確定している状態だけを記録します。作業ログとして使用せず、古い情報は最新状態へ置き換えます。

## プロジェクト概要

- 種別: Google Sheets連携の汎用売上自動集計Webアプリ
- Java: 21
- Spring Boot: 3.5.7
- Build: Maven Wrapper
- DB: PostgreSQL 17 / Spring Data JPA / Flyway V1〜V6
- Batch: Spring Batch、非同期2スレッド、execution IDでチェックポイント再開
- Schedule: Quartz JDBC JobStore、Profile別Trigger
- View: Thymeleaf / CSS / 最小限のJavaScript
- 認証: Spring Security、単一管理者、BCrypt、CSRF
- Test: JUnit 5 / Mockito / MockMvc / Testcontainers / GitHub Actions

## 完了済み

- ProfileごとにSpreadsheet、3タブ名、列マッピング、税設定、時刻、タイムゾーンを管理
- 手動実行、Quartz自動実行、Profile単位のPostgreSQL advisory lock
- 不正行の除外、商品・担当者・月別集計、結果・エラーログ出力
- チャンク処理、チェックポイント、失敗地点からの再開
- 実行開始時のProfileスナップショット保存
- Google API接続5秒・読込30秒の既定タイムアウトと再試行
- 再開試行ごとの状態、時間、エラーコード、概要の監査履歴
- 成功後の途中集計削除、既存残存データのV6清掃、完了履歴180日保持
- Profile無効化、最終実行表示、履歴の状態絞り込み・25件ページング
- Sheet直接リンク、入力支援、実行確認、二重送信防止
- Sheets出力の見出し書式、固定行、列幅調整
- 運用手順、受入チェック、2分デモ手順、CIバッジ
- `./mvnw verify` 40件成功
- Docker ComposeでV4→V6移行、health `UP`、履歴保持と途中データ削除を確認
- ローカル管理画面の一覧・設定・実行詳細をデスクトップ幅で確認し、README画像を更新
- 実Google Sheetsへ手動集計し、対象7件、正常5件、除外2件、税率`10%`を確認
- Sheetsの見出し書式、通貨書式、固定行と、エラーログ2件をAPIで再読込して確認
- 実行試行1件の完了と、成功後の3途中集計テーブル0件をDBで確認

## GitHubでの反映

- 改善内容はPR `#1 実運用品質・管理画面UX・提出品質を改善` で管理
- GitHub ActionsでMavenテストとDockerビルドを実行

## 未完了部分

- ポートフォリオ提出方法に合わせ、privateのまま共同編集者を招待するかpublicへ変更するか決定

## 既知の制約

- ヘッダー行は入力タブの1行目固定
- 入力上限は既定10,000件、DB保存する入力エラー詳細は最大500件
- 管理者は単一ユーザー
- Profile削除は提供せず、履歴保護のため無効化を使用
- 出力直前の通信断ではGoogle側の反映有無を完全判定できないが、同じ結果で上書き可能

## 次にやること

1. GitHub Actionsの成功を確認し、PR #1をmainへ反映する。
2. リポジトリ公開範囲をユーザー判断に従って設定する。

## 作業継続に必要な情報

- ローカルURL: `http://127.0.0.1:8080/admin`
- Docker ComposeのPostgreSQL公開ポート: `5433`
- 実資格情報、Spreadsheet ID、サービスアカウントJSONはこのファイルへ記録しない。
- テスト用Google Sheetは3タブ `売上データ` / `集計結果` / `エラーログ` を使用する。

## 最終更新

2026-09-01
