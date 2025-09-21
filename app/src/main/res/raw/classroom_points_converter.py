import json
import os

def parse_building_and_floor(name):
    """從名稱解析建築物和樓層"""
    name = name.strip()
    
    # 預設值
    building = ""
    building_id = 0
    floor = 0
    
    # 檢查建築物
    if name.startswith('sea'):
        building = "sea"
        building_id = 1
    elif name.startswith('seb'):
        building = "seb"
        building_id = 2
    elif name.startswith('sec'):
        building = "sec"
        building_id = 3
    
    # 嘗試提取樓層
    if len(name) >= 4 and name[3].isdigit():
        floor = int(name[3])
    
    return building, building_id, floor

def determine_image_resource(building, floor):
    """確定圖片資源名稱"""
    if floor <= 3:
        return f"R.drawable.se{floor}"
    else:
        return f"R.drawable.{building}{floor}"

# 讀取JSON文件
file_path = 'classroom_points.json'
with open(file_path, 'r') as f:
    points = json.load(f)

# 設定輸出文件路徑
output_file_path = os.path.join(os.path.dirname(file_path), 'reference_points_output.txt')

# 將輸出儲存到txt檔案
with open(output_file_path, 'w', encoding='utf-8') as output_file:
    # 生成輸出
    for point in points:
        point_id = point['id']
        name = point['name']
        x = point['x']
        y = point['y']
        
        # 解析名稱以獲取建築物和樓層
        building, building_id, floor = parse_building_and_floor(name)
        floor_id = building_id * 10 + floor  # 例如：sea1 => 11, seb3 => 23, sec2 => 32
        
        # 確定正確的圖片資源
        image_resource = determine_image_resource(building, floor)
        
        # 獲取建築物字符 (1->A, 2->B, 3->C)
        building_char = chr(64 + building_id) if building_id > 0 else ""
        
        # 生成輸出行並寫入文件
        line = f'ReferencePointEntity("{point_id}", "{name}", {x}, {y}, {image_resource}, 0, "CLASSROOM", "{building_char}", {floor_id}),'
        output_file.write(line + '\n')

print(f"輸出已保存到文件: {os.path.abspath(output_file_path)}")
