import json
import os
import sys

# 1. 設定檔案路徑 (使用絕對路徑以避免找不到檔案)
script_dir = os.path.dirname(os.path.abspath(__file__))
file_path = os.path.join(script_dir, 'entrance_point.json')
output_file_path = os.path.join(script_dir, 'reference_entrance_points_output.txt')

def get_building_info(name):
    """
    解析建築物資訊
    回傳: (建築代號字元, 建築ID, 樓層ID)
    """
    name = name.strip().lower()

    # 預設值 (如果讀不到 sea/seb/sec，預設歸類為 A 棟)
    building_char = "A"
    building_id = 1

    # 根據名稱判斷是哪一棟的入口
    if name.startswith('sea'):
        building_char = "A"
        building_id = 1
    elif name.startswith('seb'):
        building_char = "B"
        building_id = 2
    elif name.startswith('sec'):
        building_char = "C"
        building_id = 3

    # 因為是入口，且都在 se1 圖片上，我們固定設為 1 樓
    floor = 1

    # floor_id 計算公式：建築ID * 10 + 樓層
    # sea => 11, seb => 21, sec => 31
    floor_id = building_id * 10 + floor

    return building_char, floor_id

# 2. 檢查輸入檔是否存在
if not os.path.exists(file_path):
    print(f' 錯誤: 找不到輸入檔 {file_path}', file=sys.stderr)
    print(f'請確認您的 json 檔名是否為 entrance_point.json 並且與腳本在同一目錄下。')
    sys.exit(1)

# 3. 讀取與處理
try:
    with open(file_path, 'r', encoding='utf-8') as f:
        points = json.load(f)

    with open(output_file_path, 'w', encoding='utf-8') as output_file:
        count = 0
        for point in points:
            # 基本防護：確保必要欄位存在
            if 'name' not in point or 'id' not in point:
                continue

            pid = point['id']
            name = point['name']

            # 取得座標，如果 JSON 裡沒有則預設為 0
            x = point.get('x', 0)
            y = point.get('y', 0)

            # --- 核心邏輯修改 ---

            # 1. 取得建築資訊 (A/B/C) 和 floor_id
            building_char, floor_id = get_building_info(name)

            # 2. 強制指定圖片資源為 se1 (根據您的需求)
            image_resource = "R.drawable.se1"

            # 3. 生成輸出行
            # 格式: ReferencePointEntity("id", "name", x, y, image, 0, "ENTRANCE", "BuildingChar", floor_id),
            line = f'ReferencePointEntity("{pid}", "{name}", {x}, {y}, {image_resource}, 0, "ENTRANCE", "{building_char}", {floor_id}),'

            output_file.write(line + '\n')
            count += 1

    print(f' 轉換成功！已處理 {count} 筆資料。')
    print(f' 輸出檔案: {output_file_path}')

except Exception as e:
    print(f' 發生錯誤: {e}', file=sys.stderr)