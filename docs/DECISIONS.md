# 重要な設計判断

このファイルには、継続作業で再確認が必要になる重要な設計判断だけを記録します。通常の作業履歴は保存しません。

## D001 Profile単位で設定とスケジュールを管理する

### 判断

単一設定ではなく、`aggregation_profile` ごとにSpreadsheet、タブ、列、税、時刻、タイムゾーンを管理します。

### 理由

店舗や用途ごとに異なるSheetを扱い、別Profileは並行実行しつつ、同じProfileだけを排他制御するためです。

### 関連箇所

`AggregationProfileEntity`、`QuartzScheduleService`、`PostgresAdvisoryLockService`

## D002 実行開始時の設定を履歴へスナップショット保存する

### 判断

Batchは現在のProfileではなく、`aggregation_execution` に保存した開始時設定を使用します。

### 理由

実行中や失敗後にProfileが編集されても、再開時の入力元・列・出力先を変えず、再現性を保つためです。

### 関連箇所

`ExecutionProfileSnapshot`、`AggregationExecutionEntity`、`AggregationLaunchService`

## D003 再開試行を実行履歴と分離して保持する

### 判断

実行の現在状態は `aggregation_execution`、各起動・再開の状態は `aggregation_execution_attempt` に保存します。

### 理由

再開成功で主履歴が成功へ変わっても、最初の失敗原因、エラーコード、処理時間を監査可能にするためです。

### 関連箇所

Flyway V5、`ExecutionAttemptService`、`AggregationJobListener`

## D004 途中集計と監査データの寿命を分ける

### 判断

結果公開後は商品・担当者・月別の途中集計を即時削除し、実行・試行・入力エラーは既定180日保持します。

### 理由

再開に不要な作業データの無期限増加を防ぎながら、運用調査に必要な履歴は残すためです。

### 関連箇所

`AggregationWorkStore`、`PublishResultTasklet`、`ExecutionRetentionService`、Flyway V6

## D005 Profileは削除せず無効化する

### 判断

管理画面はProfile削除を提供せず、有効フラグで手動実行・再開・Quartz Triggerを停止します。

### 理由

過去履歴との外部キーと監査可能性を保ち、誤削除を避けるためです。

### 関連箇所

`AggregationProfileEntity.active`、`AggregationLaunchService`、`QuartzScheduleService`

## D006 Google Sheets出力は同じ範囲へ上書き可能にする

### 判断

結果・エラーログの既定範囲をクリアしてから、同じ実行結果を再度書き込める方式にします。

### 理由

外部APIへの書込直後に通信断が起きた場合でも、再開時に二重追加せず復旧できるようにするためです。

### 関連箇所

`GoogleSheetsGateway`
