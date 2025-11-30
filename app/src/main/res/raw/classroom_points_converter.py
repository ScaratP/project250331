import json
import os
import sys

# 強制設定標準輸出編碼，防止中文在某些環境下印不出來
sys.stdout.reconfigure(encoding='utf-8')

print("🚀 程式開始初始化...")

def parse_building_and_floor(name):
    """從名稱解析建築物和樓層"""
    name = name.strip()
    building = ""
    building_id = 0
    floor = 0
    
    if name.startswith('sea'):
        building = "sea"; building_id = 1
    elif name.startswith('seb'):
        building = "seb"; building_id = 2
    elif name.startswith('sec'):
        building = "sec"; building_id = 3
    
    if len(name) >= 4 and name[3].isdigit():
        floor = int(name[3])
    return building, building_id, floor

def determine_image_resource(building, floor):
    if floor <= 3: return f"R.drawable.se{floor}"
    else: return f"R.drawable.{building}{floor}"

# 1. 路徑設定
script_dir = os.path.dirname(os.path.abspath(__file__))
file_path = os.path.join(script_dir, 'classroom_points.json')
output_file_path = os.path.join(script_dir, 'reference_points_output.txt')

print(f"📂 正在讀取檔案: {file_path}")

# 2. 檢查檔案存在
if not os.path.exists(file_path):
    print(f"❌ 錯誤：找不到輸入文件！請確認該路徑下有檔案。")
    input("按 Enter 結束...")
    exit(1)

# 3. 讀取與解析
try:
    with open(file_path, 'r', encoding='utf-8') as f:
        points = json.load(f)
    print(f"✅ JSON 讀取成功，共發現 {len(points)} 筆原始資料。")
except json.JSONDecodeError as e:
    print(f"❌ JSON 解析錯誤: {e}")
    input("按 Enter 結束...")
    exit(2)
except Exception as e:
    print(f"❌ 發生未預期的錯誤: {e}")
    input("按 Enter 結束...")
    exit(3)

# 4. 資料處理
valid_points = []
skipped_count = 0
print("🔄 開始處理資料...")

for idx, point in enumerate(points):
    if not isinstance(point, dict):
        print(f"⚠️ 跳過第 {idx+1} 筆 (格式錯誤): 不是物件")
        skipped_count += 1
        continue
        
    required = ['id', 'name', 'x', 'y']
    missing = [k for k in required if k not in point]
    
    if missing:
        print(f"⚠️ 跳過第 {idx+1} 筆 (資料缺失): 缺少 {missing}")
        skipped_count += 1
        continue
        
    valid_points.append(point)

# 5. 寫入檔案
try:
    with open(output_file_path, 'w', encoding='utf-8') as output_file:
        for point in valid_points:
            point_id = point['id']
            name = point['name']
            x = point['x']
            y = point['y']
            
            building, building_id, floor = parse_building_and_floor(name)
            floor_id = building_id * 10 + floor
            image_resource = determine_image_resource(building, floor)
            building_char = chr(64 + building_id) if building_id > 0 else ""
            
            line = f'ReferencePointEntity("{point_id}", "{name}", {x}, {y}, {image_resource}, 0, "CLASSROOM", "{building_char}", {floor_id}),' 
            output_file.write(line + '\n')
            
    print(f"🎉 成功！已寫入 {len(valid_points)} 筆資料，跳過 {skipped_count} 筆。")
    print(f"📄 輸出位置: {output_file_path}")

except Exception as e:
    print(f"❌ 寫入檔案時發生錯誤: {e}")

# 防止視窗秒關
print("------------------------------------------------")
input("執行完成，請按 Enter 鍵離開視窗...")